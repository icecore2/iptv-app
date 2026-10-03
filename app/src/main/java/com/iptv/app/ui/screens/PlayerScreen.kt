package com.iptv.app.ui.screens

import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.ui.zIndex
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import android.app.Activity
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.os.SystemClock
import android.view.WindowManager
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.input.pointer.pointerInput
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import com.iptv.app.core.model.AppSettings
import com.iptv.app.core.model.ChannelWithEpg
import com.iptv.app.core.model.EpgProgramme
import com.iptv.app.data.PlaybackCacheManager
import com.iptv.app.ui.viewmodel.AspectRatioMode
import com.iptv.app.ui.viewmodel.PlayerViewModel
import com.iptv.app.ui.viewmodel.SettingsViewModel
import com.iptv.app.ui.viewmodel.StreamInfo
import kotlinx.coroutines.delay
import com.iptv.app.ui.components.ProgrammeDetailsBottomSheet
import com.iptv.app.ui.components.SourceBadge
import java.util.Locale

@OptIn(UnstableApi::class)
@Composable
fun PlayerScreen(
    viewModel: PlayerViewModel,
    settingsViewModel: SettingsViewModel? = null,
    favoriteIds: Set<String> = emptySet(),
    onToggleFavorite: (String) -> Unit = {},
    onBack: () -> Unit,
    onOpenSettings: () -> Unit = {}
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    val channel = uiState.currentChannel
    val settings = settingsViewModel?.settings?.collectAsState()?.value ?: AppSettings()

    var showPlayerSettingsSheet by remember { mutableStateOf(false) }
    var selectedMetadataProgramme by remember { mutableStateOf<EpgProgramme?>(null) }
    var channelSelectorTab by remember { mutableIntStateOf(0) } // 1: Live Channels, 2: VOD, 0: All

    // Screen gesture states
    var isSwipingGesture by remember { mutableStateOf(false) }
    var gestureSeekDeltaMs by remember { mutableLongStateOf(0L) }
    var doubleTapFeedback by remember { mutableStateOf<Pair<Boolean, Int>?>(null) }

    LaunchedEffect(doubleTapFeedback) {
        if (doubleTapFeedback != null) {
            delay(750)
            doubleTapFeedback = null
        }
    }

    // Screen wake lock management
    val activity = context as? Activity
    DisposableEffect(settings.keepScreenOn) {
        if (settings.keepScreenOn) {
            activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        onDispose {
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    // Reset orientation to unspecified when leaving player screen
    DisposableEffect(Unit) {
        onDispose {
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

    // ExoPlayer instance lifecycle with custom buffer control and time-shift disk caching
    val bufferSeconds = settings.bufferDurationSeconds
    val storageLimitMb = settings.bufferStorageLimitMb
    val exoPlayer = remember(bufferSeconds, storageLimitMb) {
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                /* minBufferMs = */ (bufferSeconds * 1000).coerceAtLeast(2000),
                /* maxBufferMs = */ (bufferSeconds * 2000).coerceAtLeast(5000),
                /* bufferForPlaybackMs = */ (bufferSeconds * 250).coerceIn(1000, 3000),
                /* bufferForPlaybackAfterRebufferMs = */ (bufferSeconds * 500).coerceIn(1500, 5000)
            )
            .setBackBuffer(
                /* backBufferDurationMs = */ 3_600_000, // Retain up to 1 hour back-buffer for time-shifting
                /* retainBackBufferFromKeyframe = */ true
            )
            .build()

        val mediaSourceFactory = DefaultMediaSourceFactory(context)
            .setDataSourceFactory(PlaybackCacheManager.createDataSourceFactory(context, storageLimitMb))

        ExoPlayer.Builder(context)
            .setMediaSourceFactory(mediaSourceFactory)
            .setLoadControl(loadControl)
            .build().apply {
                playWhenReady = true
            }
    }

    // Connect speed changes to player
    LaunchedEffect(uiState.playbackSpeed) {
        exoPlayer.playbackParameters = PlaybackParameters(uiState.playbackSpeed)
    }

    // Connect mute changes to player
    LaunchedEffect(uiState.isMuted) {
        exoPlayer.volume = if (uiState.isMuted) 0f else 1f
    }

    var liveSessionStartTime by remember(channel?.streamUrl) {
        mutableLongStateOf(SystemClock.elapsedRealtime())
    }

    // Track playback progress & duration for VOD and Live Buffer
    LaunchedEffect(exoPlayer, uiState.isPlaying, uiState.isVodPlayback, channel?.streamUrl) {
        liveSessionStartTime = SystemClock.elapsedRealtime()
        viewModel.resetLiveBuffer()

        while (true) {
            val cur = exoPlayer.currentPosition.coerceAtLeast(0L)
            val dur = exoPlayer.duration.let { if (it > 0) it else 0L }

            if (uiState.isVodPlayback || channel?.isVod == true) {
                viewModel.updateVodProgress(cur, dur)
            } else {
                val elapsedSinceStart = (SystemClock.elapsedRealtime() - liveSessionStartTime).coerceAtLeast(0L)
                val liveOffset = if (exoPlayer.isCurrentMediaItemLive) {
                    exoPlayer.currentLiveOffset.coerceAtLeast(0L)
                } else 0L
                val posFromStart = (elapsedSinceStart - liveOffset).coerceIn(0L, elapsedSinceStart)
                viewModel.updateLiveBufferProgress(elapsedSinceStart, posFromStart)
            }
            delay(500)
        }
    }

    DisposableEffect(Unit) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                when (playbackState) {
                    Player.STATE_BUFFERING -> viewModel.setBuffering(true)
                    Player.STATE_READY -> {
                        viewModel.setBuffering(false)
                        viewModel.setError(null)
                    }
                    Player.STATE_ENDED -> viewModel.setPlaying(false)
                    Player.STATE_IDLE -> {}
                }
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                viewModel.setPlaying(isPlaying)
            }

            override fun onPlayerError(error: PlaybackException) {
                viewModel.setError("Playback error: ${error.message ?: "Stream unavailable"}")
            }

            override fun onVideoSizeChanged(videoSize: VideoSize) {
                val currentInfo = viewModel.uiState.value.streamInfo
                val resolutionStr = if (videoSize.width > 0 && videoSize.height > 0) {
                    val qualityLabel = when {
                        videoSize.height >= 2160 -> " (4K UHD)"
                        videoSize.height >= 1080 -> " (Full HD)"
                        videoSize.height >= 720 -> " (HD)"
                        else -> " (SD)"
                    }
                    "${videoSize.width}x${videoSize.height}$qualityLabel"
                } else currentInfo.resolution

                viewModel.updateStreamInfo(
                    currentInfo.copy(
                        resolution = resolutionStr,
                        bufferPercentage = exoPlayer.bufferedPercentage
                    )
                )
            }
        }
        exoPlayer.addListener(listener)

        onDispose {
            exoPlayer.removeListener(listener)
            exoPlayer.release()
        }
    }

    // Load stream when current channel changes
    LaunchedEffect(channel?.streamUrl) {
        if (channel != null && channel.streamUrl.isNotBlank()) {
            val mediaItem = MediaItem.fromUri(channel.streamUrl)
            exoPlayer.setMediaItem(mediaItem)
            exoPlayer.prepare()
            exoPlayer.play()
        }
    }

    var isScrubbing by remember { mutableStateOf(false) }

    // Auto-hide controls timer (paused when any dialog/sheet is open or user is scrubbing)
    LaunchedEffect(uiState.isControlsVisible, uiState.isLocked, uiState.hasAnyDialogOpen, isScrubbing) {
        if (uiState.isControlsVisible && !uiState.isLocked && !uiState.hasAnyDialogOpen && !isScrubbing) {
            delay(5000)
            viewModel.setControlsVisible(false)
        }
    }

    val resizeMode = when (uiState.aspectRatioMode) {
        AspectRatioMode.FIT -> AspectRatioFrameLayout.RESIZE_MODE_FIT
        AspectRatioMode.ZOOM -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
        AspectRatioMode.FILL -> AspectRatioFrameLayout.RESIZE_MODE_FILL
    }

    val isVodMode = uiState.isVodPlayback || channel?.isVod == true

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(uiState.isLocked) {
                if (uiState.isLocked) {
                    detectTapGestures { viewModel.toggleControls() }
                } else {
                    detectTapGestures(
                        onTap = { viewModel.toggleControls() },
                        onDoubleTap = { offset ->
                            val screenWidth = size.width
                            if (offset.x < screenWidth * 0.35f) {
                                // Rewind 10s
                                val target = (exoPlayer.currentPosition - 10_000L).coerceAtLeast(0L)
                                exoPlayer.seekTo(target)
                                doubleTapFeedback = false to 10
                            } else if (offset.x > screenWidth * 0.65f) {
                                // Forward 10s
                                if (!isVodMode && uiState.timeShiftOffsetMs <= 10_000L) {
                                    exoPlayer.seekToDefaultPosition()
                                } else {
                                    val dur = if (exoPlayer.duration > 0) exoPlayer.duration else Long.MAX_VALUE
                                    val target = (exoPlayer.currentPosition + 10_000L).coerceAtMost(dur)
                                    exoPlayer.seekTo(target)
                                }
                                doubleTapFeedback = true to 10
                            } else {
                                // Center double-tap: Play/Pause toggle
                                if (exoPlayer.isPlaying) exoPlayer.pause() else exoPlayer.play()
                            }
                        }
                    )
                }
            }
            .pointerInput(uiState.isLocked, isVodMode) {
                if (!uiState.isLocked) {
                    detectHorizontalDragGestures(
                        onDragStart = {
                            isSwipingGesture = true
                            gestureSeekDeltaMs = 0L
                        },
                        onHorizontalDrag = { change, dragAmount ->
                            change.consume()
                            gestureSeekDeltaMs += (dragAmount * 80f).toLong()
                        },
                        onDragEnd = {
                            isSwipingGesture = false
                            if (gestureSeekDeltaMs != 0L) {
                                if (isVodMode) {
                                    val dur = if (exoPlayer.duration > 0) exoPlayer.duration else Long.MAX_VALUE
                                    val target = (exoPlayer.currentPosition + gestureSeekDeltaMs).coerceIn(0L, dur)
                                    exoPlayer.seekTo(target)
                                } else {
                                    if (gestureSeekDeltaMs > 0 && (uiState.timeShiftOffsetMs - gestureSeekDeltaMs) <= 3000L) {
                                        exoPlayer.seekToDefaultPosition()
                                    } else {
                                        val target = (exoPlayer.currentPosition + gestureSeekDeltaMs).coerceAtLeast(0L)
                                        exoPlayer.seekTo(target)
                                    }
                                }
                                gestureSeekDeltaMs = 0L
                            }
                        },
                        onDragCancel = {
                            isSwipingGesture = false
                            gestureSeekDeltaMs = 0L
                        }
                    )
                }
            }
    ) {
        // ExoPlayer View
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player = exoPlayer
                    useController = false
                    this.resizeMode = resizeMode
                    layoutParams = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                }
            },
            update = { playerView ->
                playerView.resizeMode = resizeMode
            },
            modifier = Modifier.fillMaxSize()
        )

        // Technical Stream Info Overlay (if enabled in settings)
        if (settings.showStreamInfoOverlay && uiState.streamInfo.streamUrl.isNotBlank()) {
            Card(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .statusBarsPadding()
                    .padding(start = 16.dp, top = 60.dp),
                colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = 0.7f)),
                shape = RoundedCornerShape(8.dp)
            ) {
                Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
                    Text(
                        text = "${uiState.streamInfo.resolution} • ${uiState.streamInfo.streamFormat}",
                        fontSize = 11.sp,
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "Buffer: ${uiState.streamInfo.bufferPercentage}% (Target: ${settings.bufferDurationSeconds}s)",
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        // Buffering indicator
        if (uiState.isBuffering && uiState.errorMessage == null) {
            CircularProgressIndicator(
                modifier = Modifier
                    .size(56.dp)
                    .align(Alignment.Center),
                color = MaterialTheme.colorScheme.primary,
                strokeWidth = 4.dp
            )
        }

        // Error overlay
        if (uiState.errorMessage != null) {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xDD200000)),
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(32.dp)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ErrorOutline,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(48.dp)
                    )
                    Text(
                        text = uiState.errorMessage!!,
                        color = Color.White,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Button(
                        onClick = {
                            channel?.let {
                                val mediaItem = MediaItem.fromUri(it.streamUrl)
                                exoPlayer.setMediaItem(mediaItem)
                                exoPlayer.prepare()
                                exoPlayer.play()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text("Retry Stream")
                    }
                }
            }
        }

        // Gesture HUD Overlay (for screen swipe scrub & double-tap feedback)
        if (isSwipingGesture || doubleTapFeedback != null) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = Color.Black.copy(alpha = 0.85f),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.25f)),
                shadowElevation = 12.dp,
                modifier = Modifier.align(Alignment.Center)
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val isFwd = if (isSwipingGesture) gestureSeekDeltaMs >= 0 else (doubleTapFeedback?.first == true)
                    Icon(
                        imageVector = if (isFwd) Icons.Default.FastForward else Icons.Default.FastRewind,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(36.dp)
                    )
                    val deltaSec = if (isSwipingGesture) (gestureSeekDeltaMs / 1000).toInt() else if (doubleTapFeedback?.first == true) 10 else -10
                    Text(
                        text = "${if (deltaSec >= 0) "+" else ""}${deltaSec}s",
                        color = Color.White,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                    if (isSwipingGesture) {
                        val targetPos = if (isVodMode) {
                            (exoPlayer.currentPosition + gestureSeekDeltaMs).coerceIn(0L, if (exoPlayer.duration > 0) exoPlayer.duration else Long.MAX_VALUE)
                        } else {
                            (exoPlayer.currentPosition + gestureSeekDeltaMs).coerceAtLeast(0L)
                        }
                        Text(
                            text = "Target: ${formatDuration(targetPos)}",
                            color = Color.White.copy(alpha = 0.8f),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }

        // Locked Screen Indicator (when locked)
        if (uiState.isLocked) {
            AnimatedVisibility(
                visible = uiState.isControlsVisible,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(top = 16.dp)
            ) {
                ElevatedFilterChip(
                    selected = true,
                    onClick = { viewModel.toggleLock() },
                    leadingIcon = {
                        Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(18.dp))
                    },
                    label = { Text("Controls Locked (Tap to Unlock)") }
                )
            }
        }

        // Overlay Controls (when unlocked)
        if (!uiState.isLocked) {
            AnimatedVisibility(
                visible = uiState.isControlsVisible,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.fillMaxSize()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.45f))
                ) {
                    // Top bar
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.TopCenter)
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(Color.Black.copy(alpha = 0.85f), Color.Transparent)
                                )
                            )
                            .statusBarsPadding()
                            .displayCutoutPadding()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = onBack) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = Color.White
                            )
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = channel?.name ?: "Media Stream",
                                    color = Color.White,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f, fill = false)
                                )

                                if (isVodMode) {
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Surface(
                                        color = MaterialTheme.colorScheme.secondary,
                                        shape = RoundedCornerShape(4.dp)
                                    ) {
                                        Text(
                                            text = "VOD",
                                            color = Color.White,
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            }

                            channel?.group?.let {
                                Text(
                                    text = it,
                                    color = Color.White.copy(alpha = 0.7f),
                                    style = MaterialTheme.typography.bodySmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        // Sleep Timer active badge (visible if timer is currently running)
                        if (uiState.sleepTimerMinutesRemaining != null) {
                            TextButton(
                                onClick = { viewModel.setSleepTimerDialogVisible(true) },
                                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.secondary),
                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Bedtime,
                                    contentDescription = "Sleep Timer",
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(3.dp))
                                Text(
                                    text = "${uiState.sleepTimerMinutesRemaining}m",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        // Aspect ratio mode cycle button
                        TextButton(
                            onClick = { viewModel.cycleAspectRatio() },
                            colors = ButtonDefaults.textButtonColors(contentColor = Color.White),
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp)
                        ) {
                            Icon(Icons.Default.AspectRatio, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                when (uiState.aspectRatioMode) {
                                    AspectRatioMode.FIT -> "Fit"
                                    AspectRatioMode.ZOOM -> "Crop"
                                    AspectRatioMode.FILL -> "Fill"
                                },
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 12.sp
                            )
                        }

                        // Screen Orientation / Fullscreen Toggle Button
                        IconButton(
                            onClick = {
                                val currentOrientation = activity?.resources?.configuration?.orientation
                                if (currentOrientation == Configuration.ORIENTATION_LANDSCAPE) {
                                    activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                                } else {
                                    activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                                }
                            }
                        ) {
                            val isLandscape = activity?.resources?.configuration?.orientation == Configuration.ORIENTATION_LANDSCAPE
                            Icon(
                                imageVector = if (isLandscape) Icons.Default.ScreenLockPortrait else Icons.Default.ScreenRotation,
                                contentDescription = if (isLandscape) "Switch to Portrait" else "Switch to Landscape",
                                tint = Color.White
                            )
                        }

                        // Lock Controls Button
                        IconButton(onClick = { viewModel.toggleLock() }) {
                            Icon(
                                imageVector = Icons.Default.LockOpen,
                                contentDescription = "Lock Controls",
                                tint = Color.White
                            )
                        }

                        // More / Settings Button (opens comprehensive Player Settings sheet)
                        IconButton(onClick = { showPlayerSettingsSheet = true }) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = "Player Settings & Options",
                                tint = Color.White
                            )
                        }
                    }

                    // Center playback controls
                    Row(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Previous channel button
                        IconButton(
                            onClick = { viewModel.playPrevious() },
                            modifier = Modifier
                                .size(48.dp)
                                .background(Color.White.copy(alpha = 0.2f), CircleShape)
                        ) {
                            Icon(
                                imageVector = Icons.Default.SkipPrevious,
                                contentDescription = "Previous Channel",
                                tint = Color.White,
                                modifier = Modifier.size(28.dp)
                            )
                        }

                        // Rewind 10 seconds (VOD or Live Buffer)
                        if (isVodMode || uiState.canGoBackToStart) {
                            IconButton(
                                onClick = {
                                    val newPos = (exoPlayer.currentPosition - 10_000L).coerceAtLeast(0L)
                                    exoPlayer.seekTo(newPos)
                                },
                                modifier = Modifier
                                    .size(52.dp)
                                    .background(Color.White.copy(alpha = 0.2f), CircleShape)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Replay10,
                                    contentDescription = "Rewind 10s",
                                    tint = Color.White,
                                    modifier = Modifier.size(32.dp)
                                )
                            }
                        }

                        // Play / Pause button
                        IconButton(
                            onClick = {
                                if (exoPlayer.isPlaying) {
                                    exoPlayer.pause()
                                } else {
                                    exoPlayer.play()
                                }
                            },
                            modifier = Modifier
                                .size(70.dp)
                                .background(MaterialTheme.colorScheme.primary, CircleShape)
                        ) {
                            Icon(
                                imageVector = if (uiState.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = if (uiState.isPlaying) "Pause" else "Play",
                                tint = Color.White,
                                modifier = Modifier.size(40.dp)
                            )
                        }

                        // Forward 10 seconds (VOD or Live Buffer if behind live)
                        if (isVodMode || !uiState.isAtLiveEdge) {
                            IconButton(
                                onClick = {
                                    if (!isVodMode && uiState.timeShiftOffsetMs <= 10_000L) {
                                        exoPlayer.seekToDefaultPosition()
                                    } else {
                                        val dur = if (exoPlayer.duration > 0) exoPlayer.duration else Long.MAX_VALUE
                                        val newPos = (exoPlayer.currentPosition + 10_000L).coerceAtMost(dur)
                                        exoPlayer.seekTo(newPos)
                                    }
                                },
                                modifier = Modifier
                                    .size(52.dp)
                                    .background(Color.White.copy(alpha = 0.2f), CircleShape)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Forward10,
                                    contentDescription = "Forward 10s",
                                    tint = Color.White,
                                    modifier = Modifier.size(32.dp)
                                )
                            }
                        }

                        // Next channel button
                        IconButton(
                            onClick = { viewModel.playNext() },
                            modifier = Modifier
                                .size(48.dp)
                                .background(Color.White.copy(alpha = 0.2f), CircleShape)
                        ) {
                            Icon(
                                imageVector = Icons.Default.SkipNext,
                                contentDescription = "Next Channel",
                                tint = Color.White,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                    }

                    // Bottom bar with EPG info, VOD scrubber, Live Time-Shift & Quick Actions
                    val currentEpg = uiState.currentEpg
                    val currentProg = uiState.activeProgramme ?: currentEpg?.currentProgramme

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.BottomCenter)
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.92f))
                                )
                            )
                            .navigationBarsPadding()
                            .displayCutoutPadding()
                            .padding(horizontal = 20.dp, vertical = 10.dp)
                    ) {
                        // Quick Action Buttons Row (Icon-only with long-press labels)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Primary Navigation Icons (Channels, EPG, VOD, and time-shift actions)
                            Row(
                                modifier = Modifier
                                    .weight(1f, fill = false)
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Channels Icon Button
                                PlayerActionButton(
                                    icon = Icons.Default.Tv,
                                    label = "Channels",
                                    onClick = {
                                        channelSelectorTab = 1
                                        viewModel.setChannelSelectorVisible(true)
                                    }
                                )

                                // EPG Guide Icon Button
                                PlayerActionButton(
                                    icon = Icons.Default.CalendarMonth,
                                    label = "EPG Guide",
                                    onClick = { viewModel.setEpgSheetVisible(true) }
                                )

                                // VOD Icon Button
                                PlayerActionButton(
                                    icon = Icons.Default.Movie,
                                    label = "VOD",
                                    onClick = {
                                        channelSelectorTab = 2
                                        viewModel.setChannelSelectorVisible(true)
                                    }
                                )

                                // Go to Starting Point Button (For Live Time-Shift)
                                if (!isVodMode && uiState.canGoBackToStart) {
                                    PlayerActionButton(
                                        icon = Icons.Default.FirstPage,
                                        label = "Go to Start",
                                        containerColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.85f),
                                        contentColor = Color.White,
                                        onClick = {
                                            val target = (exoPlayer.currentPosition - uiState.livePositionFromStartMs).coerceAtLeast(0L)
                                            exoPlayer.seekTo(target)
                                        }
                                    )
                                }

                                // Jump to Live Button (When Time-Shifted behind Live)
                                if (!isVodMode && !uiState.isAtLiveEdge) {
                                    PlayerActionButton(
                                        icon = Icons.Default.FastForward,
                                        label = "Jump to Live",
                                        containerColor = Color(0xFF2E7D32),
                                        contentColor = Color.White,
                                        onClick = { exoPlayer.seekToDefaultPosition() }
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.width(8.dp))

                            // Pinned Action Buttons (Favorite + Mute)
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                val isFav = channel?.let { favoriteIds.contains(it.id) } == true
                                PlayerActionButton(
                                    icon = if (isFav) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                    label = if (isFav) "Favorited" else "Favorite",
                                    contentColor = if (isFav) Color(0xFFFF4757) else Color.White,
                                    containerColor = Color.White.copy(alpha = 0.15f),
                                    onClick = { channel?.let { onToggleFavorite(it.id) } }
                                )

                                PlayerActionButton(
                                    icon = if (uiState.isMuted) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp,
                                    label = if (uiState.isMuted) "Unmute" else "Mute",
                                    containerColor = Color.White.copy(alpha = 0.15f),
                                    onClick = { viewModel.toggleMute() }
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        // Interactive VOD Scrubber
                        if (isVodMode && uiState.vodDurationMs > 0) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = formatDuration(uiState.vodProgressMs),
                                    color = Color.White,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold
                                )

                                EnhancedPlayerScrubber(
                                    positionMs = uiState.vodProgressMs,
                                    durationMs = uiState.vodDurationMs,
                                    bufferedPositionMs = (exoPlayer.bufferedPercentage * uiState.vodDurationMs / 100L).coerceAtLeast(0L),
                                    onSeek = { newPos ->
                                        exoPlayer.seekTo(newPos)
                                    },
                                    isLiveMode = false,
                                    programmeTitleProvider = { _ -> uiState.activeProgramme?.title },
                                    onScrubbingChanged = { isScrubbing = it },
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(horizontal = 8.dp)
                                )

                                Text(
                                    text = formatDuration(uiState.vodDurationMs),
                                    color = Color.White.copy(alpha = 0.7f),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                        }

                        // Interactive Live Stream Time-Shift Buffer Scrubber
                        if (!isVodMode && uiState.liveSessionDurationMs >= 5000L) {
                            val liveMarkers = remember(uiState.liveSessionStartTimeMs, uiState.liveSessionDurationMs, uiState.currentSchedule) {
                                if (uiState.liveSessionDurationMs <= 0L || uiState.currentSchedule.isEmpty()) {
                                    emptyList<TimelineMarker>()
                                } else {
                                    val sessionStart = uiState.liveSessionStartTimeMs
                                    val sessionEnd = sessionStart + uiState.liveSessionDurationMs
                                    uiState.currentSchedule.mapNotNull { prog ->
                                        if (prog.startEpochMillis in (sessionStart + 1000L)..(sessionEnd - 1000L)) {
                                            val frac = (prog.startEpochMillis - sessionStart).toFloat() / uiState.liveSessionDurationMs
                                            TimelineMarker(positionFraction = frac.coerceIn(0f, 1f), label = prog.title)
                                        } else null
                                    }
                                }
                            }

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = if (uiState.livePositionFromStartMs <= 1000L) "00:00 (Start)" else formatDuration(uiState.livePositionFromStartMs),
                                    color = Color.White,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold
                                )

                                EnhancedPlayerScrubber(
                                    positionMs = uiState.livePositionFromStartMs,
                                    durationMs = uiState.liveSessionDurationMs,
                                    bufferedPositionMs = uiState.liveSessionDurationMs,
                                    markers = liveMarkers,
                                    isLiveMode = true,
                                    liveOffsetMs = uiState.timeShiftOffsetMs,
                                    isAtLiveEdge = uiState.isAtLiveEdge,
                                    programmeTitleProvider = { previewPosMs ->
                                        val targetWallTime = uiState.liveSessionStartTimeMs + previewPosMs
                                        viewModel.getProgrammeAtTime(targetWallTime)?.title
                                    },
                                    onSeek = { newPosFromStart ->
                                        val seekOffsetBehindLive = (uiState.liveSessionDurationMs - newPosFromStart).coerceAtLeast(0L)
                                        if (seekOffsetBehindLive <= 3000L) {
                                            exoPlayer.seekToDefaultPosition()
                                        } else {
                                            val delta = uiState.livePositionFromStartMs - newPosFromStart
                                            val target = (exoPlayer.currentPosition - delta).coerceAtLeast(0L)
                                            exoPlayer.seekTo(target)
                                        }
                                    },
                                    onScrubbingChanged = { isScrubbing = it },
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(horizontal = 8.dp)
                                )

                                Surface(
                                    onClick = { exoPlayer.seekToDefaultPosition() },
                                    color = if (uiState.isAtLiveEdge) Color(0xFF1B5E20) else Color(0xFFE65100),
                                    shape = RoundedCornerShape(4.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(6.dp)
                                                .background(Color.White, CircleShape)
                                        )
                                        Text(
                                            text = if (uiState.isAtLiveEdge) "LIVE" else "-${formatDuration(uiState.timeShiftOffsetMs)}",
                                            color = Color.White,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                        }

                        // Programme metadata
                        if (currentProg != null) {
                            Text(
                                text = if (isVodMode) "PLAYING VOD ARCHIVE" else "NOW PLAYING",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.secondary,
                                fontWeight = FontWeight.Bold
                            )
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = currentProg.title,
                                    color = Color.White,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.weight(1f, fill = false)
                                )
                                if (settings.showInlineMetadataBadge) {
                                    SourceBadge(
                                        source = settings.preferredMetadataSource,
                                        onClick = { selectedMetadataProgramme = currentProg }
                                    )
                                }
                            }
                            if (!currentProg.description.isNullOrBlank()) {
                                Text(
                                    text = currentProg.description,
                                    color = Color.White.copy(alpha = 0.75f),
                                    style = MaterialTheme.typography.bodySmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            if (!isVodMode && currentEpg != null) {
                                Spacer(modifier = Modifier.height(6.dp))

                                LinearProgressIndicator(
                                    progress = { currentEpg.progress },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(4.dp)
                                        .clip(RoundedCornerShape(2.dp)),
                                    color = MaterialTheme.colorScheme.secondary,
                                    trackColor = Color.White.copy(alpha = 0.2f)
                                )

                                if (currentEpg.nextProgramme != null) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "Up next: ${currentEpg.nextProgramme.title}",
                                        color = Color.White.copy(alpha = 0.6f),
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                }
                            }
                        } else {
                            Text(
                                text = if (isVodMode) "VOD Playback" else "Live Broadcast (No EPG available)",
                                color = Color.White.copy(alpha = 0.6f),
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }
        }
    }

    // Modal Sheets and Dialogs inside the Player

    // 1. Stream Information Dialog
    if (uiState.isStreamInfoDialogVisible) {
        StreamInfoDialog(
            streamInfo = uiState.streamInfo,
            onDismiss = { viewModel.setStreamInfoDialogVisible(false) }
        )
    }

    // 2. Quick Channel & VOD Switcher
    if (uiState.isChannelSelectorVisible) {
        PlayerChannelSelectorSheet(
            viewModel = viewModel,
            favoriteIds = favoriteIds,
            initialTab = channelSelectorTab,
            onSelectChannel = { newChannel ->
                viewModel.setChannelSelectorVisible(false)
                viewModel.playChannel(newChannel, uiState.channelList, uiState.matcher)
            },
            onDismiss = { viewModel.setChannelSelectorVisible(false) }
        )
    }

    // 3. EPG Guide Sheet
    if (uiState.isEpgSheetVisible) {
        // In VOD mode, use the original channel for schedule lookup (not the synthetic VOD item)
        val epgChannel = if (uiState.isVodPlayback && uiState.originalChannel != null) {
            val origChannel = uiState.originalChannel!!
            uiState.matcher?.enrichChannel(origChannel) ?: ChannelWithEpg(origChannel)
        } else {
            uiState.currentEpg ?: channel?.let { ChannelWithEpg(it) }
        }

        if (epgChannel != null) {
            val schedule = uiState.matcher?.getSchedule(epgChannel.channel) ?: emptyList()
            EpgScheduleSheet(
                channelWithEpg = epgChannel,
                schedule = schedule,
                showMetadataBadge = settings.showInlineMetadataBadge,
                metadataSource = settings.preferredMetadataSource,
                onProgrammeMetadataClick = { prog ->
                    viewModel.setEpgSheetVisible(false)
                    selectedMetadataProgramme = prog
                },
                onPlayProgrammeVod = { prog, ch ->
                    viewModel.playProgrammeVod(prog, ch, uiState.channelList, uiState.matcher)
                },
                onPlayChannelLive = { ch ->
                    viewModel.playChannel(ch, uiState.channelList, uiState.matcher)
                },
                onDismiss = { viewModel.setEpgSheetVisible(false) }
            )
        } else {
            viewModel.setEpgSheetVisible(false)
        }
    }

    // 4. Playback Speed Dialog
    if (uiState.isSpeedDialogVisible) {
        PlaybackSpeedDialog(
            currentSpeed = uiState.playbackSpeed,
            onSpeedSelected = { viewModel.setPlaybackSpeed(it) },
            onDismiss = { viewModel.setSpeedDialogVisible(false) }
        )
    }

    // 5. Sleep Timer Dialog
    if (uiState.isSleepTimerDialogVisible) {
        SleepTimerDialog(
            currentMinutes = uiState.sleepTimerMinutesRemaining,
            onSetTimer = { viewModel.setSleepTimer(it) },
            onCancelTimer = { viewModel.cancelSleepTimer() },
            onDismiss = { viewModel.setSleepTimerDialogVisible(false) }
        )
    }

    // 6. Player Settings Sheet
    if (showPlayerSettingsSheet && settingsViewModel != null) {
        PlayerSettingsSheet(
            settingsViewModel = settingsViewModel,
            playerViewModel = viewModel,
            onDismiss = { showPlayerSettingsSheet = false },
            onOpenFullSettings = {
                showPlayerSettingsSheet = false
                onOpenSettings()
            }
        )
    }

    // 7. Programme Metadata Details Bottom Sheet
    val metaProg = selectedMetadataProgramme
    if (metaProg != null) {
        ProgrammeDetailsBottomSheet(
            programmeTitle = metaProg.title,
            channelName = channel?.name,
            metadataRepository = viewModel.metadataRepository,
            preferredLanguage = settings.metadataLanguage,
            preferredSource = settings.preferredMetadataSource,
            onPlayLive = { selectedMetadataProgramme = null },
            onPlayVod = {
                selectedMetadataProgramme = null
                channel?.let { ch ->
                    viewModel.playProgrammeVod(metaProg, ch, uiState.channelList, uiState.matcher)
                }
            },
            onDismiss = { selectedMetadataProgramme = null }
        )
    }
}

internal fun formatDuration(millis: Long): String {
    if (millis <= 0) return "00:00"
    val totalSeconds = millis / 1000
    val seconds = totalSeconds % 60
    val minutes = (totalSeconds / 60) % 60
    val hours = totalSeconds / 3600
    return if (hours > 0) {
        String.format(Locale.getDefault(), "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
    }
}

@Composable
private fun PlayerActionButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    containerColor: Color = Color.White.copy(alpha = 0.2f),
    contentColor: Color = Color.White,
    iconSize: androidx.compose.ui.unit.Dp = 20.dp
) {
    var showLabel by remember { mutableStateOf(false) }
    val haptics = LocalHapticFeedback.current

    LaunchedEffect(showLabel) {
        if (showLabel) {
            delay(1800)
            showLabel = false
        }
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
    ) {
        // Floating label popup above the button on long-press
        AnimatedVisibility(
            visible = showLabel,
            enter = fadeIn() + scaleIn(),
            exit = fadeOut() + scaleOut(),
            modifier = Modifier
                .offset(y = (-40).dp)
                .zIndex(10f)
        ) {
            Surface(
                color = Color.Black.copy(alpha = 0.92f),
                shape = RoundedCornerShape(8.dp),
                shadowElevation = 8.dp,
                border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.3f))
            ) {
                Text(
                    text = label,
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    softWrap = false,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }

        FilledTonalIconButton(
            onClick = onClick,
            colors = IconButtonDefaults.filledTonalIconButtonColors(
                containerColor = containerColor,
                contentColor = contentColor
            ),
            modifier = Modifier
                .size(42.dp)
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = { onClick() },
                        onLongPress = {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            showLabel = true
                        }
                    )
                }
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                modifier = Modifier.size(iconSize)
            )
        }
    }
}
