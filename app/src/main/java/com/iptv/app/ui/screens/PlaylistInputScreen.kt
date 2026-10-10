package com.iptv.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.iptv.app.core.model.SavedPlaylistPair
import com.iptv.app.data.SampleDataProvider
import com.iptv.app.ui.viewmodel.PlaylistViewModel
import com.iptv.app.ui.viewmodel.SettingsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistInputScreen(
    viewModel: PlaylistViewModel,
    settingsViewModel: SettingsViewModel? = null,
    onPlaylistLoaded: () -> Unit,
    onOpenSettings: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()

    var playlistName by remember { mutableStateOf("") }
    var playlistUrl by remember { mutableStateOf("") }
    var epgUrl by remember { mutableStateOf("") }

    var editingPair by remember { mutableStateOf<SavedPlaylistPair?>(null) }
    var pairToDelete by remember { mutableStateOf<SavedPlaylistPair?>(null) }

    // Navigate when channels become loaded (only on transitions, not on initial composition)
    var hasNavigated by remember { mutableStateOf(false) }
    LaunchedEffect(uiState.channels.size, uiState.isLoading) {
        if (uiState.channels.isNotEmpty() && !uiState.isLoading && !hasNavigated) {
            hasNavigated = true
            onPlaylistLoaded()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("IPTV Stream & EPG Player", fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(onClick = onOpenSettings) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Settings"
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
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp)
                .navigationBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Icon(
                imageVector = Icons.Default.LiveTv,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(64.dp)
            )

            Text(
                text = "Welcome to IPTV Player",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )

            Text(
                text = "Select a saved playlist or add a new M3U playlist & XMLTV guide.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            // Saved Playlists Section
            if (uiState.savedPlaylists.isNotEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Saved Playlists & EPGs",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Badge(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    ) {
                        Text("${uiState.savedPlaylists.size}")
                    }
                }

                Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    uiState.savedPlaylists.forEach { pair ->
                        val isActive = pair.id == uiState.activePairId

                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    hasNavigated = false
                                    viewModel.switchToPlaylist(pair)
                                },
                            colors = CardDefaults.cardColors(
                                containerColor = if (isActive) {
                                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
                                } else {
                                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                }
                            ),
                            border = if (isActive) {
                                CardDefaults.outlinedCardBorder().copy(
                                    brush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.primary)
                                )
                            } else null,
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = if (isActive) Icons.Default.CheckCircle else Icons.Outlined.Circle,
                                    contentDescription = if (isActive) "Active" else "Inactive",
                                    tint = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                                    modifier = Modifier.size(24.dp)
                                )

                                Spacer(modifier = Modifier.width(12.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = pair.name,
                                            fontWeight = if (isActive) FontWeight.Bold else FontWeight.SemiBold,
                                            style = MaterialTheme.typography.bodyLarge,
                                            color = if (isActive) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        if (pair.isSample) {
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Surface(
                                                color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.2f),
                                                shape = RoundedCornerShape(6.dp)
                                            ) {
                                                Text(
                                                    text = "Demo",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.secondary,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                )
                                            }
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(2.dp))

                                    Text(
                                        text = pair.playlistUrl,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )

                                    if (!pair.epgUrl.isNullOrBlank()) {
                                        Text(
                                            text = "EPG: ${pair.epgUrl}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }

                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    IconButton(
                                        onClick = { editingPair = pair },
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Edit,
                                            contentDescription = "Edit Playlist",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }

                                    IconButton(
                                        onClick = { pairToDelete = pair },
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Delete,
                                            contentDescription = "Delete Playlist",
                                            tint = MaterialTheme.colorScheme.error,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(4.dp))

            // Add or Load Custom Playlist Card
            Text(
                text = "Add New IPTV Playlist",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = playlistName,
                onValueChange = { playlistName = it },
                label = { Text("Playlist Name (Optional)") },
                placeholder = { Text("e.g. My Cable, Sports TV") },
                leadingIcon = {
                    Icon(Icons.Default.Edit, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                },
                trailingIcon = {
                    if (playlistName.isNotBlank()) {
                        IconButton(onClick = { playlistName = "" }) {
                            Icon(Icons.Default.Close, contentDescription = "Clear")
                        }
                    }
                },
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            OutlinedTextField(
                value = playlistUrl,
                onValueChange = { playlistUrl = it },
                label = { Text("M3U / M3U8 Playlist URL") },
                placeholder = { Text("https://example.com/playlist.m3u") },
                leadingIcon = {
                    Icon(Icons.Default.LiveTv, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                },
                trailingIcon = {
                    if (playlistUrl.isNotBlank()) {
                        IconButton(onClick = { playlistUrl = "" }) {
                            Icon(Icons.Default.Close, contentDescription = "Clear")
                        }
                    }
                },
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth(),
                singleLine = false,
                maxLines = 3
            )

            OutlinedTextField(
                value = epgUrl,
                onValueChange = { epgUrl = it },
                label = { Text("XMLTV EPG URL (.xml or .xml.gz - Optional)") },
                placeholder = { Text("https://example.com/epg.xml.gz") },
                leadingIcon = {
                    Icon(Icons.Default.CalendarMonth, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                },
                trailingIcon = {
                    if (epgUrl.isNotBlank()) {
                        IconButton(onClick = { epgUrl = "" }) {
                            Icon(Icons.Default.Close, contentDescription = "Clear")
                        }
                    }
                },
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth(),
                singleLine = false,
                maxLines = 3
            )

            // Quick Fill Demo / Sample Data Chip
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Start
            ) {
                SuggestionChip(
                    onClick = {
                        playlistName = "Demo TV & VOD Streams"
                        playlistUrl = SampleDataProvider.DEFAULT_SAMPLE_PLAYLIST_URL
                        epgUrl = SampleDataProvider.DEFAULT_SAMPLE_EPG_URL
                    },
                    label = { Text("Fill with Free Demo Streams") },
                    icon = {
                        Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                    }
                )
            }

            if (uiState.error != null) {
                Text(
                    text = uiState.error!!,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center
                )
            }

            // Save and Watch button
            Button(
                onClick = {
                    if (playlistUrl.isNotBlank()) {
                        hasNavigated = false
                        val resolvedName = playlistName.trim().ifBlank { "Playlist ${uiState.savedPlaylists.size + 1}" }
                        viewModel.saveAndSwitch(
                            name = resolvedName,
                            playlistUrl = playlistUrl.trim(),
                            epgUrl = if (epgUrl.isNotBlank()) epgUrl.trim() else null
                        )
                    }
                },
                enabled = !uiState.isLoading && playlistUrl.isNotBlank(),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
            ) {
                if (uiState.isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Loading & Parsing Playlist...")
                } else {
                    Icon(Icons.Default.PlayArrow, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Save & Watch")
                }
            }

            // Load Once button
            OutlinedButton(
                onClick = {
                    if (playlistUrl.isNotBlank()) {
                        hasNavigated = false
                        viewModel.loadPlaylist(
                            url = playlistUrl.trim(),
                            explicitEpgUrl = if (epgUrl.isNotBlank()) epgUrl.trim() else null
                        )
                    }
                },
                enabled = !uiState.isLoading && playlistUrl.isNotBlank(),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
            ) {
                Icon(Icons.Default.Refresh, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Load Once (Without Saving)")
            }
        }
    }

    // Edit Dialog
    editingPair?.let { pair ->
        PlaylistEditDialog(
            initialPair = pair,
            onDismiss = { editingPair = null },
            onSave = { name, updatedUrl, updatedEpg ->
                viewModel.updatePlaylist(
                    pair.copy(
                        name = name,
                        playlistUrl = updatedUrl,
                        epgUrl = updatedEpg
                    )
                )
                editingPair = null
            }
        )
    }

    // Delete Confirmation Dialog
    pairToDelete?.let { pair ->
        AlertDialog(
            onDismissRequest = { pairToDelete = null },
            title = { Text("Delete Saved Playlist?") },
            text = {
                Text("Are you sure you want to remove \"${pair.name}\" from your saved playlists?")
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deletePlaylist(pair.id)
                        pairToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { pairToDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}
