package com.iptv.app.ui.viewmodel

import android.content.Context
import android.graphics.Bitmap
import android.view.TextureView
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import com.iptv.app.core.matcher.CatchupResolver
import com.iptv.app.core.matcher.EpgMatcher
import com.iptv.app.core.model.ChannelWithEpg
import com.iptv.app.core.model.EpgProgramme
import com.iptv.app.core.model.M3uItem
import com.iptv.app.data.PlaybackCacheManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.lang.ref.WeakReference

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
    val activeProgramme: EpgProgramme? = null,
    val isVodPlayback: Boolean = false,
    val vodProgressMs: Long = 0L,
    val vodDurationMs: Long = 0L,
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
    val isSleepTimerDialogVisible: Boolean = false,
    val originalChannel: M3uItem? = null,

    // Time-Shift Replay Buffer for Live Streams
    val liveSessionStartTimeMs: Long = 0L,
    val liveSessionDurationMs: Long = 0L,
    val livePositionFromStartMs: Long = 0L,
    val isAtLiveEdge: Boolean = true,
    val timeShiftOffsetMs: Long = 0L,
    val canGoBackToStart: Boolean = false,

    // EPG Schedule for timeline programme markers and scrubber tooltip
    val currentSchedule: List<EpgProgramme> = emptyList()
) {
    val hasAnyDialogOpen: Boolean
        get() = isStreamInfoDialogVisible || isEpgSheetVisible ||
                isChannelSelectorVisible || isSpeedDialogVisible ||
                isSleepTimerDialogVisible
}

class PlayerViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(PlayerUiState())
    val uiState: StateFlow<PlayerUiState> = _uiState.asStateFlow()

    private val _channelThumbnails = MutableStateFlow<Map<String, Bitmap>>(emptyMap())
    val channelThumbnails: StateFlow<Map<String, Bitmap>> = _channelThumbnails.asStateFlow()

    private var activePlayerView: WeakReference<PlayerView>? = null
    private var exoPlayer: ExoPlayer? = null

    val metadataRepository: com.iptv.app.data.ProgrammeMetadataRepository = com.iptv.app.data.ProgrammeMetadataRepository()
    private var sleepTimerJob: Job? = null

    fun registerPlayerView(playerView: PlayerView) {
        activePlayerView = WeakReference(playerView)
    }

    fun unregisterPlayerView(playerView: PlayerView) {
        if (activePlayerView?.get() == playerView) {
            activePlayerView = null
        }
    }

    fun saveChannelThumbnail(channelId: String, bitmap: Bitmap) {
        _channelThumbnails.update { current ->
            val updated = current.toMutableMap()
            if (updated.size >= 20 && !updated.containsKey(channelId)) {
                val oldestKey = updated.keys.firstOrNull()
                if (oldestKey != null) {
                    updated.remove(oldestKey)
                }
            }
            updated[channelId] = bitmap
            updated
        }
    }

    fun getChannelThumbnail(channelId: String): Bitmap? {
        return _channelThumbnails.value[channelId]
    }

    fun captureCurrentFrame(): Bitmap? {
        val playerView = activePlayerView?.get() ?: return null
        val surfaceView = playerView.videoSurfaceView
        if (surfaceView is TextureView) {
            val w = surfaceView.width
            val h = surfaceView.height
            if (w > 0 && h > 0) {
                return surfaceView.getBitmap(w, h)
            }
        }
        return null
    }

    fun scaleThumbnail(bitmap: Bitmap, maxDimension: Int = 320): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        if (width <= 0 || height <= 0 || (width <= maxDimension && height <= maxDimension)) return bitmap
        val ratio = width.toFloat() / height.toFloat()
        val targetWidth: Int
        val targetHeight: Int
        if (width > height) {
            targetWidth = maxDimension
            targetHeight = (maxDimension / ratio).toInt().coerceAtLeast(1)
        } else {
            targetHeight = maxDimension
            targetWidth = (maxDimension * ratio).toInt().coerceAtLeast(1)
        }
        return Bitmap.createScaledBitmap(bitmap, targetWidth, targetHeight, true)
    }

    fun captureAndSaveCurrentFrame() {
        val current = _uiState.value.currentChannel ?: return
        val bitmap = captureCurrentFrame() ?: return
        val scaled = scaleThumbnail(bitmap)
        saveChannelThumbnail(current.id, scaled)
    }

    fun getOrCreatePlayer(
        context: Context,
        bufferDurationSeconds: Int = 5,
        bufferStorageLimitMb: Int = 100
    ): ExoPlayer {
        exoPlayer?.let { return it }

        val appContext = context.applicationContext
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                (bufferDurationSeconds * 1000).coerceAtLeast(2000),
                (bufferDurationSeconds * 2000).coerceAtLeast(5000),
                (bufferDurationSeconds * 250).coerceIn(1000, 3000),
                (bufferDurationSeconds * 500).coerceIn(1500, 5000)
            )
            .setBackBuffer(3_600_000, true)
            .build()

        val mediaSourceFactory = DefaultMediaSourceFactory(appContext)
            .setDataSourceFactory(PlaybackCacheManager.createDataSourceFactory(appContext, bufferStorageLimitMb))

        val player = ExoPlayer.Builder(appContext)
            .setMediaSourceFactory(mediaSourceFactory)
            .setLoadControl(loadControl)
            .build().apply {
                playWhenReady = true
                volume = if (_uiState.value.isMuted) 0f else 1f
                playbackParameters = PlaybackParameters(_uiState.value.playbackSpeed)
            }

        attachPlayerListener(player)
        exoPlayer = player

        // If a channel is already selected, start playback on new player
        val cur = _uiState.value.currentChannel
        if (cur != null && cur.streamUrl.isNotBlank()) {
            val mediaItem = MediaItem.fromUri(cur.streamUrl)
            player.setMediaItem(mediaItem)
            player.prepare()
            player.play()
        }

        return player
    }

    fun getPlayer(): ExoPlayer? = exoPlayer

    private fun attachPlayerListener(player: ExoPlayer) {
        player.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                when (playbackState) {
                    Player.STATE_BUFFERING -> setBuffering(true)
                    Player.STATE_READY -> {
                        setBuffering(false)
                        setError(null)
                    }
                    Player.STATE_ENDED -> setPlaying(false)
                    Player.STATE_IDLE -> {}
                }
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                setPlaying(isPlaying)
            }

            override fun onPlayerError(error: PlaybackException) {
                setError("Playback error: ${error.message ?: "Stream unavailable"}")
            }

            override fun onVideoSizeChanged(videoSize: VideoSize) {
                val currentInfo = _uiState.value.streamInfo
                val resolutionStr = if (videoSize.width > 0 && videoSize.height > 0) {
                    val qualityLabel = when {
                        videoSize.height >= 2160 -> " (4K UHD)"
                        videoSize.height >= 1080 -> " (Full HD)"
                        videoSize.height >= 720 -> " (HD)"
                        else -> " (SD)"
                    }
                    "${videoSize.width}x${videoSize.height}$qualityLabel"
                } else currentInfo.resolution

                updateStreamInfo(
                    currentInfo.copy(
                        resolution = resolutionStr,
                        bufferPercentage = player.bufferedPercentage
                    )
                )
            }
        })
    }

    fun playChannel(channel: M3uItem, playlist: List<M3uItem>, matcher: EpgMatcher? = null) {
        // Capture frame of current channel before switching
        if (_uiState.value.currentChannel != null && _uiState.value.currentChannel?.id != channel.id) {
            captureAndSaveCurrentFrame()
        }

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
                activeProgramme = enriched.currentProgramme,
                isVodPlayback = channel.isVod,
                vodProgressMs = 0L,
                vodDurationMs = 0L,
                liveSessionStartTimeMs = System.currentTimeMillis(),
                liveSessionDurationMs = 0L,
                livePositionFromStartMs = 0L,
                isAtLiveEdge = true,
                timeShiftOffsetMs = 0L,
                canGoBackToStart = false,
                currentSchedule = matcher?.getSchedule(channel) ?: emptyList(),
                channelList = playlist,
                matcher = matcher,
                errorMessage = null,
                isBuffering = true,
                originalChannel = null,
                streamInfo = StreamInfo(
                    streamFormat = format,
                    streamUrl = channel.streamUrl
                )
            )
        }

        exoPlayer?.let { player ->
            if (channel.streamUrl.isNotBlank()) {
                val currentUri = player.currentMediaItem?.localConfiguration?.uri?.toString()
                if (currentUri != channel.streamUrl) {
                    val mediaItem = MediaItem.fromUri(channel.streamUrl)
                    player.setMediaItem(mediaItem)
                    player.prepare()
                    player.play()
                } else if (!player.isPlaying) {
                    player.play()
                }
            }
        }
    }

    fun playProgrammeVod(
        programme: EpgProgramme,
        channel: M3uItem,
        playlist: List<M3uItem> = emptyList(),
        matcher: EpgMatcher? = null
    ) {
        if (_uiState.value.currentChannel != null) {
            captureAndSaveCurrentFrame()
        }

        val vodUrl = CatchupResolver.buildVodUrl(channel, programme)
        val vodItem = M3uItem(
            id = "${channel.id}_vod_${programme.startEpochMillis}",
            name = programme.title,
            streamUrl = vodUrl,
            group = "${channel.name} • VOD Catchup",
            logoUrl = programme.iconUrl ?: channel.logoUrl,
            tvgId = channel.tvgId,
            tvgName = channel.tvgName,
            headers = channel.headers,
            isRadio = false,
            isCatchup = true
        )
        val durationMs = (programme.stopEpochMillis - programme.startEpochMillis).coerceAtLeast(0L)
        val enriched = ChannelWithEpg(
            channel = vodItem,
            currentProgramme = programme
        )
        val format = when {
            vodUrl.endsWith(".m3u8", ignoreCase = true) -> "HLS Catchup (.m3u8)"
            vodUrl.endsWith(".mp4", ignoreCase = true) -> "MP4 Video"
            vodUrl.endsWith(".mkv", ignoreCase = true) -> "MKV Video"
            vodUrl.endsWith(".ts", ignoreCase = true) -> "MPEG-TS Catchup (.ts)"
            else -> "VOD Stream (Catchup Archive)"
        }

        _uiState.update {
            it.copy(
                currentChannel = vodItem,
                currentEpg = enriched,
                activeProgramme = programme,
                isVodPlayback = true,
                vodProgressMs = 0L,
                vodDurationMs = durationMs,
                liveSessionStartTimeMs = 0L,
                liveSessionDurationMs = 0L,
                livePositionFromStartMs = 0L,
                isAtLiveEdge = true,
                timeShiftOffsetMs = 0L,
                canGoBackToStart = false,
                currentSchedule = matcher?.getSchedule(channel) ?: emptyList(),
                channelList = playlist,
                matcher = matcher,
                errorMessage = null,
                isBuffering = true,
                originalChannel = channel,
                streamInfo = StreamInfo(
                    streamFormat = format,
                    streamUrl = vodUrl
                )
            )
        }

        exoPlayer?.let { player ->
            if (vodUrl.isNotBlank()) {
                val mediaItem = MediaItem.fromUri(vodUrl)
                player.setMediaItem(mediaItem)
                player.prepare()
                player.play()
            }
        }
    }

    private var lastMatchedProgramme: EpgProgramme? = null

    fun getProgrammeAtTime(epochMillis: Long): EpgProgramme? {
        val cached = lastMatchedProgramme
        if (cached != null && epochMillis in cached.startEpochMillis until cached.stopEpochMillis) {
            return cached
        }
        val match = _uiState.value.currentSchedule.firstOrNull { prog ->
            epochMillis in prog.startEpochMillis until prog.stopEpochMillis
        }
        lastMatchedProgramme = match
        return match
    }

    fun updateVodProgress(currentPosMs: Long, durationMs: Long) {
        _uiState.update {
            it.copy(
                vodProgressMs = currentPosMs,
                vodDurationMs = if (durationMs > 0) durationMs else it.vodDurationMs
            )
        }
    }

    fun updateLiveBufferProgress(sessionDurationMs: Long, positionFromStartMs: Long) {
        val safeSessionDur = sessionDurationMs.coerceAtLeast(0L)
        val safePos = positionFromStartMs.coerceIn(0L, safeSessionDur)
        val offset = (safeSessionDur - safePos).coerceAtLeast(0L)
        val atLive = offset <= 3000L

        _uiState.update {
            it.copy(
                liveSessionDurationMs = safeSessionDur,
                livePositionFromStartMs = safePos,
                timeShiftOffsetMs = offset,
                isAtLiveEdge = atLive,
                canGoBackToStart = safeSessionDur >= 5000L
            )
        }
    }

    fun resetLiveBuffer() {
        _uiState.update {
            it.copy(
                liveSessionStartTimeMs = System.currentTimeMillis(),
                liveSessionDurationMs = 0L,
                livePositionFromStartMs = 0L,
                timeShiftOffsetMs = 0L,
                isAtLiveEdge = true,
                canGoBackToStart = false
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

    fun setAspectRatioMode(mode: AspectRatioMode) {
        _uiState.update { it.copy(aspectRatioMode = mode) }
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
        val newMuted = !_uiState.value.isMuted
        _uiState.update { it.copy(isMuted = newMuted) }
        exoPlayer?.volume = if (newMuted) 0f else 1f
    }

    fun setMuted(muted: Boolean) {
        _uiState.update { it.copy(isMuted = muted) }
        exoPlayer?.volume = if (muted) 0f else 1f
    }

    fun setPlaybackSpeed(speed: Float) {
        _uiState.update { it.copy(playbackSpeed = speed) }
        exoPlayer?.playbackParameters = PlaybackParameters(speed)
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

    fun stopPlayback() {
        exoPlayer?.stop()
        _uiState.update { it.copy(isPlaying = false, isBuffering = false) }
    }

    override fun onCleared() {
        super.onCleared()
        exoPlayer?.release()
        exoPlayer = null
    }
}
