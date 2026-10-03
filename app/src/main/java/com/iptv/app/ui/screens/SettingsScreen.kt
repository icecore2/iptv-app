package com.iptv.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.iptv.app.core.model.AppSettings
import com.iptv.app.ui.viewmodel.AspectRatioMode
import com.iptv.app.ui.viewmodel.SettingsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onBack: () -> Unit
) {
    val settings by viewModel.settings.collectAsState()
    var selectedTab by remember { mutableIntStateOf(0) } // 0: General, 1: Player

    var showResetConfirm by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                windowInsets = TopAppBarDefaults.windowInsets
            )
        },
        contentWindowInsets = ScaffoldDefaults.contentWindowInsets
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            PrimaryTabRow(
                selectedTabIndex = selectedTab,
                containerColor = MaterialTheme.colorScheme.surface
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Tune, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("General", fontWeight = if (selectedTab == 0) FontWeight.Bold else FontWeight.Normal)
                        }
                    }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Movie, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Metadata", fontWeight = if (selectedTab == 1) FontWeight.Bold else FontWeight.Normal)
                        }
                    }
                )
                Tab(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.PlayCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Player", fontWeight = if (selectedTab == 2) FontWeight.Bold else FontWeight.Normal)
                        }
                    }
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 16.dp)
                    .navigationBarsPadding(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                when (selectedTab) {
                    0 -> GeneralSettingsSection(
                        settings = settings,
                        viewModel = viewModel,
                        onShowResetConfirm = { showResetConfirm = true }
                    )
                    1 -> MetadataSettingsSection(
                        settings = settings,
                        viewModel = viewModel
                    )
                    2 -> PlayerSettingsSection(
                        settings = settings,
                        viewModel = viewModel
                    )
                }
            }
        }
    }

    if (showResetConfirm) {
        AlertDialog(
            onDismissRequest = { showResetConfirm = false },
            title = { Text("Reset Settings?") },
            text = { Text("All application and player settings will be reset to default values.") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.resetToDefaults()
                        showResetConfirm = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Reset to Defaults")
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetConfirm = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun GeneralSettingsSection(
    settings: AppSettings,
    viewModel: SettingsViewModel,
    onShowResetConfirm: () -> Unit
) {
    Text(
        text = "Channel List & Interface",
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary
    )

    // Images in Channels Toggle
    SettingToggleCard(
        icon = Icons.Default.Image,
        title = "Images in Channels (Logos)",
        description = "Load channel logo thumbnails from URLs. Turn off to save mobile data and dramatically accelerate scrolling.",
        checked = settings.showChannelLogos,
        onCheckedChange = { viewModel.toggleChannelLogos(it) }
    )

    // Pagination Toggle
    SettingToggleCard(
        icon = Icons.Default.VerticalSplit,
        title = "Pagination with Lazy Load",
        description = "Load channels in virtualized chunks as you scroll. Prevents lag and memory issues on playlists with thousands of channels.",
        checked = settings.enablePagination,
        onCheckedChange = { viewModel.togglePagination(it) }
    )

    // Page Size Selector
    if (settings.enablePagination) {
        Card(
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Channels Per Batch (Page Size)",
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.bodyLarge
                )
                Text(
                    text = "Controls how many channels load into memory per batch.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    AppSettings.PAGE_SIZE_OPTIONS.forEach { size ->
                        FilterChip(
                            selected = settings.pageSize == size,
                            onClick = { viewModel.setPageSize(size) },
                            label = { Text("$size") }
                        )
                    }
                }
            }
        }
    }

    // Show EPG In List Toggle
    SettingToggleCard(
        icon = Icons.Default.CalendarMonth,
        title = "Show EPG in Channel List",
        description = "Display current and upcoming programme titles and live progress bars directly under channel names.",
        checked = settings.showEpgInList,
        onCheckedChange = { viewModel.toggleShowEpgInList(it) }
    )

    // Auto-load Last Playlist
    SettingToggleCard(
        icon = Icons.Default.Autorenew,
        title = "Auto-load Last Active Playlist",
        description = "Automatically resume the last active IPTV playlist and guide when the app starts up.",
        checked = settings.autoLoadLastPlaylist,
        onCheckedChange = { viewModel.toggleAutoLoadLastPlaylist(it) }
    )

    Spacer(modifier = Modifier.height(8.dp))
    HorizontalDivider()
    Spacer(modifier = Modifier.height(8.dp))

    // Reset Defaults Button
    OutlinedButton(
        onClick = onShowResetConfirm,
        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
        modifier = Modifier.fillMaxWidth()
    ) {
        Icon(Icons.Default.RestartAlt, contentDescription = null)
        Spacer(modifier = Modifier.width(8.dp))
        Text("Reset All Settings to Defaults")
    }
}

@Composable
private fun MetadataSettingsSection(
    settings: AppSettings,
    viewModel: SettingsViewModel
) {
    var showTraktDialog by remember { mutableStateOf(false) }
    var showTvdbDialog by remember { mutableStateOf(false) }
    var tempTraktId by remember(settings.traktClientId) { mutableStateOf(settings.traktClientId) }
    var tempTvdbKey by remember(settings.tvdbApiKey) { mutableStateOf(settings.tvdbApiKey) }

    Text(
        text = "Metadata & EPG Integrations",
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary
    )

    // Summary Card
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Info,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(28.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = "When an EPG is loaded with the playlist, programme titles are automatically matched with IMDb, Trakt, sratim.co.il, and TheTVDB to display posters, localized plot descriptions, ratings, and trailers.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }

    // Inline Badges Toggle
    SettingToggleCard(
        icon = Icons.Default.Label,
        title = "Show Source Icon Inline",
        description = "Display IMDb, Trakt, sratim.co.il, or TheTVDB badge inline next to the programme name. Tap the badge to view the details split window.",
        checked = settings.showInlineMetadataBadge,
        onCheckedChange = { viewModel.toggleInlineMetadataBadge(it) }
    )

    // Preferred Source Selector
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Preferred Metadata Source",
                fontWeight = FontWeight.SemiBold,
                style = MaterialTheme.typography.bodyLarge
            )
            Text(
                text = "Priority source for initial lookup. All sources remain accessible via tabs in the details window.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                com.iptv.app.core.metadata.MetadataSource.entries.forEach { src ->
                    FilterChip(
                        selected = settings.preferredMetadataSource == src,
                        onClick = { viewModel.setPreferredMetadataSource(src) },
                        label = { Text(src.displayName) }
                    )
                }
            }
        }
    }

    // Preferred Description Language Selector
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Description Language",
                fontWeight = FontWeight.SemiBold,
                style = MaterialTheme.typography.bodyLarge
            )
            Text(
                text = "Preferred language for plot overviews, synopses, and genre tags.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(10.dp))
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                AppSettings.METADATA_LANGUAGE_OPTIONS.forEach { (code, label) ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { viewModel.setMetadataLanguage(code) }
                            .padding(vertical = 4.dp)
                    ) {
                        RadioButton(
                            selected = settings.metadataLanguage == code,
                            onClick = { viewModel.setMetadataLanguage(code) }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = label, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }

    // Trakt Client ID
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                tempTraktId = settings.traktClientId
                showTraktDialog = true
            }
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.Key, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = "Trakt Client ID", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyLarge)
                Text(
                    text = if (settings.traktClientId.isNotBlank()) "Configured (${settings.traktClientId.take(8)}...)" else "Using default public demo client",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }

    // TheTVDB API Key
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                tempTvdbKey = settings.tvdbApiKey
                showTvdbDialog = true
            }
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.VpnKey, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = "TheTVDB API Key", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyLarge)
                Text(
                    text = if (settings.tvdbApiKey.isNotBlank()) "Configured (${settings.tvdbApiKey.take(8)}...)" else "Using default project key",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }

    if (showTraktDialog) {
        AlertDialog(
            onDismissRequest = { showTraktDialog = false },
            title = { Text("Trakt API Client ID") },
            text = {
                Column {
                    Text(
                        text = "Enter your Trakt API Client ID (from trakt.tv/oauth/applications). Leave blank to use the built-in fallback.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = tempTraktId,
                        onValueChange = { tempTraktId = it },
                        label = { Text("Client ID") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    viewModel.setTraktClientId(tempTraktId.trim())
                    showTraktDialog = false
                }) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showTraktDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showTvdbDialog) {
        AlertDialog(
            onDismissRequest = { showTvdbDialog = false },
            title = { Text("TheTVDB v4 API Key") },
            text = {
                Column {
                    Text(
                        text = "Enter your TheTVDB Project API Key (from thetvdb.com). Leave blank to use the built-in fallback.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = tempTvdbKey,
                        onValueChange = { tempTvdbKey = it },
                        label = { Text("API Key") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    viewModel.setTvdbApiKey(tempTvdbKey.trim())
                    showTvdbDialog = false
                }) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showTvdbDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun PlayerSettingsSection(
    settings: AppSettings,
    viewModel: SettingsViewModel
) {
    Text(
        text = "Streaming & Playback",
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary
    )

    // Storage Meter & Replay Buffer Card
    StorageMeterCard(
        settings = settings,
        settingsViewModel = viewModel
    )

    // Buffer Size Card
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Speed,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = "ExoPlayer Buffer Size",
                        fontWeight = FontWeight.SemiBold,
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Text(
                        text = "Controls stream pre-buffering duration. Higher values smooth out unstable IPTV connections; lower values speed up channel changes.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                AppSettings.BUFFER_PRESETS.forEach { (seconds, label) ->
                    val isSelected = settings.bufferDurationSeconds == seconds
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { viewModel.setBufferDuration(seconds) }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = isSelected,
                            onClick = { viewModel.setBufferDuration(seconds) }
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
    }

    // Keep Screen On Toggle
    SettingToggleCard(
        icon = Icons.Default.WbSunny,
        title = "Keep Screen On During Playback",
        description = "Prevents device display from locking or sleeping while watching video streams.",
        checked = settings.keepScreenOn,
        onCheckedChange = { viewModel.toggleKeepScreenOn(it) }
    )

    // Fast Channel Switching Toggle
    SettingToggleCard(
        icon = Icons.Default.FlashOn,
        title = "Fast Channel Zapping",
        description = "Instantly starts playing channels when tapped in channel selector sheets without delay.",
        checked = settings.fastChannelSwitching,
        onCheckedChange = { viewModel.toggleFastChannelSwitching(it) }
    )

    // Hardware Acceleration Toggle
    SettingToggleCard(
        icon = Icons.Default.Memory,
        title = "Hardware Video Acceleration",
        description = "Leverage device hardware decoders for smooth 60fps 1080p and 4K playback.",
        checked = settings.hardwareAcceleration,
        onCheckedChange = { viewModel.toggleHardwareAcceleration(it) }
    )

    // Default Aspect Ratio
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.AspectRatio,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = "Default Aspect Ratio",
                        fontWeight = FontWeight.SemiBold,
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Text(
                        text = "Preferred scaling mode when opening a channel.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                listOf(
                    AspectRatioMode.FIT to "Fit (Letterbox)",
                    AspectRatioMode.ZOOM to "Crop (Zoom)",
                    AspectRatioMode.FILL to "Fill (Stretch)"
                ).forEach { (mode, label) ->
                    FilterChip(
                        selected = settings.defaultAspectRatio == mode,
                        onClick = { viewModel.setDefaultAspectRatio(mode) },
                        label = { Text(label, fontSize = 12.sp) }
                    )
                }
            }
        }
    }

    // Stream Technical Info Overlay
    SettingToggleCard(
        icon = Icons.Default.Info,
        title = "Stream Debug Info Overlay",
        description = "Display live stream bitrate, video codec, resolution, and buffer percentage overlay during playback.",
        checked = settings.showStreamInfoOverlay,
        onCheckedChange = { viewModel.toggleStreamInfoOverlay(it) }
    )
}

@Composable
fun SettingToggleCard(
    icon: ImageVector,
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onCheckedChange(!checked) }
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (checked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(26.dp)
            )

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.bodyLarge
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.width(10.dp))

            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange
            )
        }
    }
}
