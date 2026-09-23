package com.iptv.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import com.iptv.app.core.matcher.EpgMatcher
import com.iptv.app.core.model.ChannelWithEpg
import com.iptv.app.core.model.M3uItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

enum class AspectRatioMode {
    FIT,   // Letterbox / Pillarbox
    ZOOM,  // Crop to fill screen
    FILL   // Stretch to fill screen
}

data class PlayerUiState(
    val currentChannel: M3uItem? = null,
    val currentEpg: ChannelWithEpg? = null,
    val channelList: List<M3uItem> = emptyList(),
    val matcher: EpgMatcher? = null,
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val errorMessage: String? = null,
    val aspectRatioMode: AspectRatioMode = AspectRatioMode.FIT,
    val isControlsVisible: Boolean = true
)

class PlayerViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(PlayerUiState())
    val uiState: StateFlow<PlayerUiState> = _uiState.asStateFlow()

    fun playChannel(channel: M3uItem, playlist: List<M3uItem>, matcher: EpgMatcher? = null) {
        val enriched = matcher?.enrichChannel(channel) ?: ChannelWithEpg(channel)
        _uiState.update {
            it.copy(
                currentChannel = channel,
                currentEpg = enriched,
                channelList = playlist,
                matcher = matcher,
                errorMessage = null,
                isBuffering = true
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
        _uiState.update { it.copy(isControlsVisible = !it.isControlsVisible) }
    }

    fun setControlsVisible(visible: Boolean) {
        _uiState.update { it.copy(isControlsVisible = visible) }
    }
}
