package com.iptv.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.iptv.app.core.matcher.EpgMatcher
import com.iptv.app.core.model.ChannelWithEpg
import com.iptv.app.core.model.M3uItem
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class AspectRatioMode {
    FIT,   // Letterbox / Pillarbox
    ZOOM,  // Crop to fill screen
    FILL   // Stretch to fill screen
}

data class StreamInfo(
    val resolution: String = "Auto",
    val bitrate: String = "Auto",
    val videoCodec: String = "Auto",
    val audioCodec: String = "Auto",
    val frameRate: String = "Auto",
    val streamFormat: String = "HLS (.m3u8)",
    val streamUrl: String = "",
    val bufferPercentage: Int = 0
)

data class PlayerUiState(
    val currentChannel: M3uItem? = null,
    val currentEpg: ChannelWithEpg? = null,
    val channelList: List<M3uItem> = emptyList(),
    val matcher: EpgMatcher? = null,
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val errorMessage: String? = null,
    val aspectRatioMode: AspectRatioMode = AspectRatioMode.FIT,
    val isControlsVisible: Boolean = true,
    val isLocked: Boolean = false,
    val isMuted: Boolean = false,
    val playbackSpeed: Float = 1.0f,
    val sleepTimerMinutesRemaining: Int? = null,
    val streamInfo: StreamInfo = StreamInfo(),
    val isStreamInfoDialogVisible: Boolean = false,
    val isEpgSheetVisible: Boolean = false,
    val isChannelSelectorVisible: Boolean = false,
    val isSpeedDialogVisible: Boolean = false,
    val isSleepTimerDialogVisible: Boolean = false
)

class PlayerViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(PlayerUiState())
    val uiState: StateFlow<PlayerUiState> = _uiState.asStateFlow()

    private var sleepTimerJob: Job? = null

    fun playChannel(channel: M3uItem, playlist: List<M3uItem>, matcher: EpgMatcher? = null) {
        val enriched = matcher?.enrichChannel(channel) ?: ChannelWithEpg(channel)
        val format = when {
            channel.streamUrl.endsWith(".m3u8", ignoreCase = true) -> "HLS (.m3u8)"
            channel.streamUrl.endsWith(".mpd", ignoreCase = true) -> "DASH (.mpd)"
            channel.streamUrl.endsWith(".mp4", ignoreCase = true) -> "MP4 Video"
            channel.streamUrl.endsWith(".mkv", ignoreCase = true) -> "MKV Video"
            channel.streamUrl.endsWith(".ts", ignoreCase = true) -> "MPEG-TS (.ts)"
            else -> "Live Media Stream"
        }

        _uiState.update {
            it.copy(
                currentChannel = channel,
                currentEpg = enriched,
                channelList = playlist,
                matcher = matcher,
                errorMessage = null,
                isBuffering = true,
                streamInfo = StreamInfo(
                    streamFormat = format,
                    streamUrl = channel.streamUrl
                )
            )
        }
    }

    fun playNext() {
        val state = _uiState.value
        val list = state.channelList
        if (list.isEmpty()) return
        val currentIndex = list.indexOfFirst { it.streamUrl == state.currentChannel?.streamUrl }
        val nextIndex = if (currentIndex == -1 || currentIndex >= list.size - 1) 0 else currentIndex + 1
        val nextChannel = list[nextIndex]
        playChannel(nextChannel, list, state.matcher)
    }

    fun playPrevious() {
        val state = _uiState.value
        val list = state.channelList
        if (list.isEmpty()) return
        val currentIndex = list.indexOfFirst { it.streamUrl == state.currentChannel?.streamUrl }
        val prevIndex = if (currentIndex <= 0) list.size - 1 else currentIndex - 1
        val prevChannel = list[prevIndex]
        playChannel(prevChannel, list, state.matcher)
    }

    fun cycleAspectRatio() {
        _uiState.update {
            val nextMode = when (it.aspectRatioMode) {
                AspectRatioMode.FIT -> AspectRatioMode.ZOOM
                AspectRatioMode.ZOOM -> AspectRatioMode.FILL
                AspectRatioMode.FILL -> AspectRatioMode.FIT
            }
            it.copy(aspectRatioMode = nextMode)
        }
    }

    fun setPlaying(playing: Boolean) {
        _uiState.update { it.copy(isPlaying = playing) }
    }

    fun setBuffering(buffering: Boolean) {
        _uiState.update { it.copy(isBuffering = buffering) }
    }

    fun setError(error: String?) {
        _uiState.update { it.copy(errorMessage = error, isBuffering = false) }
    }

    fun toggleControls() {
        if (_uiState.value.isLocked) {
            // When locked, only show unlock prompt
            _uiState.update { it.copy(isControlsVisible = !it.isControlsVisible) }
            return
        }
        _uiState.update { it.copy(isControlsVisible = !it.isControlsVisible) }
    }

    fun setControlsVisible(visible: Boolean) {
        _uiState.update { it.copy(isControlsVisible = visible) }
    }

    fun toggleLock() {
        _uiState.update { it.copy(isLocked = !it.isLocked) }
    }

    fun toggleMute() {
        _uiState.update { it.copy(isMuted = !it.isMuted) }
    }

    fun setPlaybackSpeed(speed: Float) {
        _uiState.update { it.copy(playbackSpeed = speed) }
    }

    fun updateStreamInfo(info: StreamInfo) {
        _uiState.update { it.copy(streamInfo = info) }
    }

    fun setSleepTimer(minutes: Int) {
        sleepTimerJob?.cancel()
        _uiState.update { it.copy(sleepTimerMinutesRemaining = minutes) }

        sleepTimerJob = viewModelScope.launch {
            var remaining = minutes
            while (remaining > 0) {
                delay(60_000L)
                remaining--
                _uiState.update { it.copy(sleepTimerMinutesRemaining = remaining) }
            }
            // Timer expired -> pause playback
            setPlaying(false)
            _uiState.update { it.copy(sleepTimerMinutesRemaining = null) }
        }
    }

    fun cancelSleepTimer() {
        sleepTimerJob?.cancel()
        _uiState.update { it.copy(sleepTimerMinutesRemaining = null) }
    }

    fun setStreamInfoDialogVisible(visible: Boolean) {
        _uiState.update { it.copy(isStreamInfoDialogVisible = visible) }
    }

    fun setEpgSheetVisible(visible: Boolean) {
        _uiState.update { it.copy(isEpgSheetVisible = visible) }
    }

    fun setChannelSelectorVisible(visible: Boolean) {
        _uiState.update { it.copy(isChannelSelectorVisible = visible) }
    }

    fun setSpeedDialogVisible(visible: Boolean) {
        _uiState.update { it.copy(isSpeedDialogVisible = visible) }
    }

    fun setSleepTimerDialogVisible(visible: Boolean) {
        _uiState.update { it.copy(isSleepTimerDialogVisible = visible) }
    }
}
