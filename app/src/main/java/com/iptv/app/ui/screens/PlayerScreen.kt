package com.iptv.app.ui.screens

import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.iptv.app.core.model.M3uItem
import com.iptv.app.ui.viewmodel.AspectRatioMode
import com.iptv.app.ui.viewmodel.PlayerViewModel
import com.iptv.app.ui.viewmodel.StreamInfo
import kotlinx.coroutines.delay

@OptIn(UnstableApi::class)
@Composable
fun PlayerScreen(
    viewModel: PlayerViewModel,
    favoriteIds: Set<String> = emptySet(),
    onToggleFavorite: (String) -> Unit = {},
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    val channel = uiState.currentChannel

    // ExoPlayer instance lifecycle
    val exoPlayer = remember {
        ExoPlayer.Builder(context).build().apply {
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

    // Auto-hide controls timer
    LaunchedEffect(uiState.isControlsVisible, uiState.isLocked) {
        if (uiState.isControlsVisible && !uiState.isLocked) {
            delay(5000)
            viewModel.setControlsVisible(false)
        }
    }

    val resizeMode = when (uiState.aspectRatioMode) {
        AspectRatioMode.FIT -> AspectRatioFrameLayout.RESIZE_MODE_FIT
        AspectRatioMode.ZOOM -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
        AspectRatioMode.FILL -> AspectRatioFrameLayout.RESIZE_MODE_FILL
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { viewModel.toggleControls() }
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
                            Text(
                                text = channel?.name ?: "Live Stream",
                                color = Color.White,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            channel?.group?.let {
                                Text(
                                    text = it,
                                    color = Color.White.copy(alpha = 0.7f),
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }

                        // Sleep Timer Button
                        IconButton(onClick = { viewModel.setSleepTimerDialogVisible(true) }) {
                            Icon(
                                imageVector = Icons.Default.Bedtime,
                                contentDescription = "Sleep Timer",
                                tint = if (uiState.sleepTimerMinutesRemaining != null) MaterialTheme.colorScheme.secondary else Color.White
                            )
                        }

                        // Playback Speed Button
                        TextButton(
                            onClick = { viewModel.setSpeedDialogVisible(true) },
                            colors = ButtonDefaults.textButtonColors(contentColor = Color.White)
                        ) {
                            Text("${uiState.playbackSpeed}x", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }

                        // Aspect ratio mode button
                        TextButton(
                            onClick = { viewModel.cycleAspectRatio() },
                            colors = ButtonDefaults.textButtonColors(contentColor = Color.White)
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

                        // Stream Info Button
                        IconButton(onClick = { viewModel.setStreamInfoDialogVisible(true) }) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = "Stream Information",
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
                    }

                    // Center playback & channel navigation controls
                    Row(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalArrangement = Arrangement.spacedBy(28.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Previous channel button
                        IconButton(
                            onClick = { viewModel.playPrevious() },
                            modifier = Modifier
                                .size(52.dp)
                                .background(Color.White.copy(alpha = 0.2f), CircleShape)
                        ) {
                            Icon(
                                imageVector = Icons.Default.SkipPrevious,
                                contentDescription = "Previous Channel",
                                tint = Color.White,
                                modifier = Modifier.size(32.dp)
                            )
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

                        // Next channel button
                        IconButton(
                            onClick = { viewModel.playNext() },
                            modifier = Modifier
                                .size(52.dp)
                                .background(Color.White.copy(alpha = 0.2f), CircleShape)
                        ) {
                            Icon(
                                imageVector = Icons.Default.SkipNext,
                                contentDescription = "Next Channel",
                                tint = Color.White,
                                modifier = Modifier.size(32.dp)
                            )
                        }
                    }

                    // Bottom bar with EPG info & Quick Actions
                    val currentEpg = uiState.currentEpg
                    val currentProg = currentEpg?.currentProgramme

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
                        // Quick Action Buttons Row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                // Channels / VOD Selector
                                FilledTonalButton(
                                    onClick = { viewModel.setChannelSelectorVisible(true) },
                                    colors = ButtonDefaults.filledTonalButtonColors(
                                        containerColor = Color.White.copy(alpha = 0.2f),
                                        contentColor = Color.White
                                    ),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                ) {
                                    Icon(Icons.Default.VideoLibrary, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Channels & VOD", fontSize = 12.sp)
                                }

                                // EPG Guide Sheet
                                FilledTonalButton(
                                    onClick = { viewModel.setEpgSheetVisible(true) },
                                    colors = ButtonDefaults.filledTonalButtonColors(
                                        containerColor = Color.White.copy(alpha = 0.2f),
                                        contentColor = Color.White
                                    ),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                ) {
                                    Icon(Icons.Default.CalendarMonth, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("EPG Guide", fontSize = 12.sp)
                                }
                            }

                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                // Favorite Button
                                val isFav = channel?.let { favoriteIds.contains(it.id) } == true
                                IconButton(onClick = { channel?.let { onToggleFavorite(it.id) } }) {
                                    Icon(
                                        imageVector = if (isFav) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                        contentDescription = "Favorite",
                                        tint = if (isFav) Color(0xFFFF4757) else Color.White
                                    )
                                }

                                // Mute Button
                                IconButton(onClick = { viewModel.toggleMute() }) {
                                    Icon(
                                        imageVector = if (uiState.isMuted) Icons.Default.VolumeOff else Icons.Default.VolumeUp,
                                        contentDescription = "Mute",
                                        tint = Color.White
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        // EPG Information
                        if (currentProg != null) {
                            Text(
                                text = "NOW PLAYING",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.secondary,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = currentProg.title,
                                color = Color.White,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            if (!currentProg.description.isNullOrBlank()) {
                                Text(
                                    text = currentProg.description,
                                    color = Color.White.copy(alpha = 0.75f),
                                    style = MaterialTheme.typography.bodySmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

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
                        } else {
                            Text(
                                text = "Live Broadcast (No EPG available)",
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
            onSelectChannel = { newChannel ->
                viewModel.playChannel(newChannel, uiState.channelList, uiState.matcher)
            },
            onDismiss = { viewModel.setChannelSelectorVisible(false) }
        )
    }

    // 3. EPG Guide Sheet
    if (uiState.isEpgSheetVisible && uiState.currentEpg != null) {
        val schedule = uiState.matcher?.getSchedule(uiState.currentEpg!!.channel) ?: emptyList()
        EpgScheduleSheet(
            channelWithEpg = uiState.currentEpg!!,
            schedule = schedule,
            onDismiss = { viewModel.setEpgSheetVisible(false) }
        )
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
}
