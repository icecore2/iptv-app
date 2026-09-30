package com.iptv.app.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.SdStorage
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.iptv.app.core.model.AppSettings
import com.iptv.app.data.PlaybackCacheManager
import com.iptv.app.ui.viewmodel.SettingsViewModel
import java.util.Locale

/**
 * Reusable Compose component rendering a storage meter and buffer storage controls.
 */
@Composable
fun StorageMeterCard(
    settings: AppSettings,
    settingsViewModel: SettingsViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val usedBytes by PlaybackCacheManager.storageUsedBytes.collectAsState()
    var showClearDialog by remember { mutableStateOf(false) }

    // Periodic or initial refresh of buffer storage
    LaunchedEffect(Unit) {
        settingsViewModel.refreshBufferStorage(context)
    }

    val limitBytes = settings.bufferStorageLimitMb * 1024L * 1024L
    val usageRatio = if (limitBytes > 0) (usedBytes.toFloat() / limitBytes).coerceIn(0f, 1f) else 0f
    val animatedProgress by animateFloatAsState(targetValue = usageRatio, label = "storageProgress")

    val usedMb = usedBytes / (1024f * 1024f)
    val limitMb = settings.bufferStorageLimitMb
    val freeSpaceGb = PlaybackCacheManager.getFreeDiskSpaceBytes(context) / (1024f * 1024f * 1024f)

    val progressColor = when {
        usageRatio > 0.90f -> MaterialTheme.colorScheme.error
        usageRatio > 0.70f -> Color(0xFFFFA000) // Amber
        else -> MaterialTheme.colorScheme.primary
    }

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Header Row
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.SdStorage,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Time-Shift Replay Storage Meter",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Caches watched live stream segments so you can rewind back to the starting point.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Meter Stats Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                Column {
                    Text(
                        text = "BUFFER USAGE",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = String.format(Locale.US, "%.1f MB / %d MB", usedMb, limitMb),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                Text(
                    text = String.format(Locale.US, "%.0f%% full", usageRatio * 100f),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = progressColor
                )
            }

            // Visual Progress Meter Bar
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(12.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(MaterialTheme.colorScheme.surface)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(fraction = animatedProgress)
                        .clip(RoundedCornerShape(6.dp))
                        .background(progressColor)
                )
            }

            // Device Free Space
            Text(
                text = String.format(Locale.US, "Free storage on device: %.1f GB", freeSpaceGb),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)

            // Preset Storage Limit Options
            Text(
                text = "Allocated Storage Limit",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                AppSettings.BUFFER_STORAGE_PRESETS.forEach { (mb, label) ->
                    val isSelected = settings.bufferStorageLimitMb == mb
                    FilterChip(
                        selected = isSelected,
                        onClick = {
                            settingsViewModel.setBufferStorageLimit(mb)
                            PlaybackCacheManager.getCache(context, mb)
                        },
                        label = {
                            Text(
                                text = label.substringBefore(" ("),
                                fontSize = 11.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // Clear Buffer Action Button
            OutlinedButton(
                onClick = { showClearDialog = true },
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.error
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Default.DeleteSweep,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Clear Buffer Storage")
            }
        }
    }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text("Clear Buffer Storage?") },
            text = {
                Text("This will purge all temporarily cached stream segments from your device storage. You can continue streaming normally.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        settingsViewModel.clearBufferCache(context)
                        showClearDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Clear Now")
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}
