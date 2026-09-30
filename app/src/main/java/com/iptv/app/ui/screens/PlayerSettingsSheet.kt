package com.iptv.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.iptv.app.core.model.AppSettings
import com.iptv.app.ui.viewmodel.AspectRatioMode
import com.iptv.app.ui.viewmodel.PlayerViewModel
import com.iptv.app.ui.viewmodel.SettingsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerSettingsSheet(
    settingsViewModel: SettingsViewModel,
    playerViewModel: PlayerViewModel,
    onDismiss: () -> Unit,
    onOpenFullSettings: () -> Unit
) {
    val settings by settingsViewModel.settings.collectAsState()
    val playerUiState by playerViewModel.uiState.collectAsState()

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Player Settings",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                }

                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Close")
                }
            }

            // Storage Meter Card
            StorageMeterCard(
                settings = settings,
                settingsViewModel = settingsViewModel
            )

            // Buffer Size Presets
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Speed,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Playback Buffer Size", fontWeight = FontWeight.SemiBold)
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    AppSettings.BUFFER_PRESETS.forEach { (seconds, label) ->
                        val isSelected = settings.bufferDurationSeconds == seconds
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { settingsViewModel.setBufferDuration(seconds) }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = isSelected,
                                onClick = { settingsViewModel.setBufferDuration(seconds) }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = label,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                }
            }

            // Playback Speed Controls
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Speed,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Playback Speed", fontWeight = FontWeight.SemiBold)
                        Spacer(modifier = Modifier.weight(1f))
                        Text(
                            text = "${playerUiState.playbackSpeed}x",
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            fontSize = 13.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        listOf(0.75f, 1.0f, 1.25f, 1.5f, 2.0f).forEach { speed ->
                            FilterChip(
                                selected = playerUiState.playbackSpeed == speed,
                                onClick = { playerViewModel.setPlaybackSpeed(speed) },
                                label = { Text("${speed}x") }
                            )
                        }
                    }
                }
            }

            // Aspect Ratio Controls
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.AspectRatio,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Scaling / Aspect Ratio", fontWeight = FontWeight.SemiBold)
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        listOf(
                            AspectRatioMode.FIT to "Fit",
                            AspectRatioMode.ZOOM to "Crop",
                            AspectRatioMode.FILL to "Fill"
                        ).forEach { (mode, label) ->
                            FilterChip(
                                selected = playerUiState.aspectRatioMode == mode,
                                onClick = {
                                    playerViewModel.setAspectRatioMode(mode)
                                    settingsViewModel.setDefaultAspectRatio(mode)
                                },
                                label = { Text(label) }
                            )
                        }
                    }
                }
            }

            // Sleep Timer Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        onDismiss()
                        playerViewModel.setSleepTimerDialogVisible(true)
                    }
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Bedtime,
                    contentDescription = null,
                    tint = if (playerUiState.sleepTimerMinutesRemaining != null) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("Sleep Timer", fontWeight = FontWeight.SemiBold)
                    Text(
                        text = if (playerUiState.sleepTimerMinutesRemaining != null) {
                            "Active: ${playerUiState.sleepTimerMinutesRemaining}m remaining"
                        } else {
                            "Set automatic playback turn-off timer"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                TextButton(onClick = {
                    onDismiss()
                    playerViewModel.setSleepTimerDialogVisible(true)
                }) {
                    Text(if (playerUiState.sleepTimerMinutesRemaining != null) "Edit" else "Set")
                }
            }

            // Stream Technical Info Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        onDismiss()
                        playerViewModel.setStreamInfoDialogVisible(true)
                    }
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Info, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("Stream Technical Information", fontWeight = FontWeight.SemiBold)
                    Text(
                        text = "${playerUiState.streamInfo.resolution.ifBlank { "Live Stream" }} • ${playerUiState.streamInfo.streamFormat}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                TextButton(onClick = {
                    onDismiss()
                    playerViewModel.setStreamInfoDialogVisible(true)
                }) {
                    Text("View")
                }
            }

            // Keep Screen On Toggle
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { settingsViewModel.toggleKeepScreenOn(!settings.keepScreenOn) }
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.WbSunny, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("Keep Screen Awake", fontWeight = FontWeight.SemiBold)
                    Text(
                        "Don't turn off screen during playback",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = settings.keepScreenOn,
                    onCheckedChange = { settingsViewModel.toggleKeepScreenOn(it) }
                )
            }

            // Stream Info Overlay Toggle
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { settingsViewModel.toggleStreamInfoOverlay(!settings.showStreamInfoOverlay) }
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Info, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("Stream Technical Overlay", fontWeight = FontWeight.SemiBold)
                    Text(
                        "Show live codec, resolution & buffer on screen",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = settings.showStreamInfoOverlay,
                    onCheckedChange = { settingsViewModel.toggleStreamInfoOverlay(it) }
                )
            }

            HorizontalDivider()

            // Open Full App Settings Button
            OutlinedButton(
                onClick = {
                    onDismiss()
                    onOpenFullSettings()
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Open Full Settings")
            }
        }
    }
}
