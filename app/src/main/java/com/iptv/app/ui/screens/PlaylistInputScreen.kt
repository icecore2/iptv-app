package com.iptv.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.iptv.app.data.SampleDataProvider
import com.iptv.app.ui.viewmodel.PlaylistUiState
import com.iptv.app.ui.viewmodel.PlaylistViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistInputScreen(
    viewModel: PlaylistViewModel,
    onPlaylistLoaded: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()

    var playlistUrl by remember { mutableStateOf(SampleDataProvider.DEFAULT_SAMPLE_PLAYLIST_URL) }
    var epgUrl by remember { mutableStateOf(SampleDataProvider.DEFAULT_SAMPLE_EPG_URL) }

    // When playlist has items and not loading, navigate
    LaunchedEffect(uiState.channels.isNotEmpty(), uiState.isLoading) {
        if (uiState.channels.isNotEmpty() && !uiState.isLoading) {
            onPlaylistLoaded()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("IPTV Stream & EPG Player", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Icon(
                imageVector = Icons.Default.LiveTv,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(72.dp)
            )

            Text(
                text = "Welcome to IPTV Player",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )

            Text(
                text = "Load an M3U/M3U8 playlist and optional XMLTV EPG guide to start watching live channels.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Demo channels card
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Quick Start: Pre-Configured Channels",
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Text(
                        text = "Test the app immediately with working public streams (News, Science, Sports, Movies) and synchronized EPG guide.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f)
                    )
                    Button(
                        onClick = { viewModel.loadSampleData() },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Load Demo Channels & EPG")
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(8.dp))

            // Custom M3U URL field
            OutlinedTextField(
                value = playlistUrl,
                onValueChange = { playlistUrl = it },
                label = { Text("M3U / M3U8 Playlist URL") },
                placeholder = { Text("https://example.com/playlist.m3u") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = false,
                maxLines = 3
            )

            // Custom EPG URL field
            OutlinedTextField(
                value = epgUrl,
                onValueChange = { epgUrl = it },
                label = { Text("XMLTV EPG URL (.xml or .xml.gz - Optional)") },
                placeholder = { Text("https://example.com/epg.xml.gz") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = false,
                maxLines = 3
            )

            if (uiState.error != null) {
                Text(
                    text = uiState.error!!,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center
                )
            }

            Button(
                onClick = {
                    if (playlistUrl.isNotBlank()) {
                        viewModel.loadPlaylist(
                            url = playlistUrl.trim(),
                            explicitEpgUrl = if (epgUrl.isNotBlank()) epgUrl.trim() else null
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
                    Icon(Icons.Default.Refresh, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Load Custom Playlist")
                }
            }
        }
    }
}
