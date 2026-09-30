package com.iptv.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.iptv.app.core.model.SavedPlaylistPair

@Composable
fun PlaylistEditDialog(
    initialPair: SavedPlaylistPair? = null,
    onDismiss: () -> Unit,
    onSave: (name: String, playlistUrl: String, epgUrl: String?) -> Unit
) {
    var name by remember { mutableStateOf(initialPair?.name ?: "") }
    var playlistUrl by remember { mutableStateOf(initialPair?.playlistUrl ?: "") }
    var epgUrl by remember { mutableStateOf(initialPair?.epgUrl ?: "") }
    var nameError by remember { mutableStateOf(false) }
    var urlError by remember { mutableStateOf(false) }

    val isEdit = initialPair != null

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (isEdit) "Edit Saved Playlist" else "Save Playlist & EPG Pair",
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = {
                        name = it
                        if (nameError && it.isNotBlank()) nameError = false
                    },
                    label = { Text("Playlist Name") },
                    placeholder = { Text("e.g. Home Cable, Sports UK") },
                    isError = nameError,
                    supportingText = if (nameError) {
                        { Text("Name cannot be empty") }
                    } else null,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = playlistUrl,
                    onValueChange = {
                        playlistUrl = it
                        if (urlError && it.isNotBlank()) urlError = false
                    },
                    label = { Text("M3U / M3U8 Playlist URL") },
                    placeholder = { Text("https://example.com/playlist.m3u") },
                    isError = urlError,
                    supportingText = if (urlError) {
                        { Text("Playlist URL cannot be empty") }
                    } else null,
                    singleLine = false,
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = epgUrl,
                    onValueChange = { epgUrl = it },
                    label = { Text("XMLTV EPG URL (Optional)") },
                    placeholder = { Text("https://example.com/epg.xml.gz") },
                    singleLine = false,
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val trimmedName = name.trim()
                    val trimmedUrl = playlistUrl.trim()
                    if (trimmedName.isBlank()) {
                        nameError = true
                    }
                    if (trimmedUrl.isBlank()) {
                        urlError = true
                    }
                    if (trimmedName.isNotBlank() && trimmedUrl.isNotBlank()) {
                        onSave(
                            trimmedName,
                            trimmedUrl,
                            if (epgUrl.isNotBlank()) epgUrl.trim() else null
                        )
                    }
                }
            ) {
                Text(if (isEdit) "Update" else "Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
        shape = RoundedCornerShape(16.dp)
    )
}
