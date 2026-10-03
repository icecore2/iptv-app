package com.iptv.app.ui.components

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.iptv.app.core.metadata.MetadataSource
import com.iptv.app.core.metadata.ProgrammeMetadata
import com.iptv.app.data.ProgrammeMetadataRepository
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProgrammeDetailsBottomSheet(
    programmeTitle: String,
    channelName: String? = null,
    metadataRepository: ProgrammeMetadataRepository,
    preferredLanguage: String = "en",
    preferredSource: MetadataSource = MetadataSource.AUTO,
    onPlayLive: (() -> Unit)? = null,
    onPlayVod: (() -> Unit)? = null,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
    ) {
        ProgrammeDetailsContent(
            programmeTitle = programmeTitle,
            channelName = channelName,
            metadataRepository = metadataRepository,
            preferredLanguage = preferredLanguage,
            preferredSource = preferredSource,
            onPlayLive = onPlayLive,
            onPlayVod = onPlayVod,
            onClose = onDismiss,
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.92f)
                .navigationBarsPadding()
        )
    }
}

@Composable
fun ProgrammeDetailsSidePanel(
    programmeTitle: String,
    channelName: String? = null,
    metadataRepository: ProgrammeMetadataRepository,
    preferredLanguage: String = "en",
    preferredSource: MetadataSource = MetadataSource.AUTO,
    onPlayLive: (() -> Unit)? = null,
    onPlayVod: (() -> Unit)? = null,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxHeight()
            .width(420.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 6.dp,
        shadowElevation = 8.dp
    ) {
        ProgrammeDetailsContent(
            programmeTitle = programmeTitle,
            channelName = channelName,
            metadataRepository = metadataRepository,
            preferredLanguage = preferredLanguage,
            preferredSource = preferredSource,
            onPlayLive = onPlayLive,
            onPlayVod = onPlayVod,
            onClose = onClose,
            modifier = Modifier.fillMaxSize()
        )
    }
}

@Composable
fun ProgrammeDetailsContent(
    programmeTitle: String,
    channelName: String? = null,
    metadataRepository: ProgrammeMetadataRepository,
    preferredLanguage: String = "en",
    preferredSource: MetadataSource = MetadataSource.AUTO,
    onPlayLive: (() -> Unit)? = null,
    onPlayVod: (() -> Unit)? = null,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var sourcesData by remember(programmeTitle, preferredLanguage) {
        mutableStateOf<Map<MetadataSource, ProgrammeMetadata>>(emptyMap())
    }
    var activeSource by remember(sourcesData, preferredSource) {
        mutableStateOf(
            if (preferredSource != MetadataSource.AUTO && sourcesData.containsKey(preferredSource)) {
                preferredSource
            } else {
                sourcesData.keys.firstOrNull() ?: MetadataSource.IMDB
            }
        )
    }
    var isLoading by remember(programmeTitle, preferredLanguage) { mutableStateOf(true) }

    LaunchedEffect(programmeTitle, preferredLanguage) {
        isLoading = true
        val resolved = metadataRepository.resolveAllSources(programmeTitle, preferredLanguage)
        sourcesData = resolved
        if (resolved.isNotEmpty()) {
            activeSource = if (preferredSource != MetadataSource.AUTO && resolved.containsKey(preferredSource)) {
                preferredSource
            } else {
                resolved.keys.first()
            }
        }
        isLoading = false
    }

    val currentMeta = sourcesData[activeSource]

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        // Header Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = programmeTitle,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (!channelName.isNullOrBlank()) {
                    Text(
                        text = "Channel: $channelName",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            IconButton(onClick = onClose) {
                Icon(Icons.Default.Close, contentDescription = "Close")
            }
        }

        // Source Switcher Tabs
        ScrollableTabRow(
            selectedTabIndex = MetadataSource.CONCRETE_SOURCES.indexOf(activeSource).coerceAtLeast(0),
            edgePadding = 0.dp,
            containerColor = Color.Transparent,
            modifier = Modifier.fillMaxWidth()
        ) {
            MetadataSource.CONCRETE_SOURCES.forEach { src ->
                val hasData = sourcesData.containsKey(src)
                Tab(
                    selected = activeSource == src,
                    onClick = { activeSource = src },
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = src.displayName,
                                fontWeight = if (activeSource == src) FontWeight.Bold else FontWeight.Normal
                            )
                            if (hasData) {
                                Spacer(modifier = Modifier.width(4.dp))
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(RoundedCornerShape(3.dp))
                                        .background(Color(src.brandColorHex))
                                )
                            }
                        }
                    }
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Content Body
        if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(modifier = Modifier.size(36.dp))
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Fetching metadata from IMDb, Trakt, Sratim & TVDB...",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else if (currentMeta == null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.SearchOff,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "No detailed information found on ${activeSource.displayName}.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (sourcesData.isNotEmpty()) {
                        Text(
                            text = "Try switching to another source tab above.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        } else {
            // Detailed View
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
            ) {
                // Poster + Key Facts
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    if (!currentMeta.posterUrl.isNullOrBlank()) {
                        AsyncImage(
                            model = currentMeta.posterUrl,
                            contentDescription = currentMeta.title,
                            modifier = Modifier
                                .width(115.dp)
                                .height(165.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .width(115.dp)
                                .height(165.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Movie,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(40.dp)
                            )
                        }
                    }

                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = currentMeta.title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )

                        // Year + Release Date
                        val yearText = currentMeta.year?.toString() ?: ""
                        val dateText = currentMeta.releaseDate ?: ""
                        if (yearText.isNotBlank() || dateText.isNotBlank()) {
                            Text(
                                text = listOf(yearText, dateText).filter { it.isNotBlank() }.joinToString(" • "),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        // Rating badge
                        if (currentMeta.rating != null && currentMeta.rating > 0.0) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = Color(0xFFF5C518).copy(alpha = 0.2f)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Star,
                                        contentDescription = null,
                                        tint = Color(0xFFF5C518),
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = String.format(java.util.Locale.US, "%.1f / 10", currentMeta.rating),
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    if (currentMeta.ratingCount != null) {
                                        Text(
                                            text = " (${currentMeta.ratingCount} votes)",
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }

                        // Director
                        if (!currentMeta.director.isNullOrBlank()) {
                            Text(
                                text = "Director: ${currentMeta.director}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        // Source pill
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = Color(activeSource.brandColorHex).copy(alpha = 0.15f)
                        ) {
                            Text(
                                text = "Source: ${activeSource.displayName}",
                                color = Color(activeSource.brandColorHex),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }

                // Action Buttons: Play Trailer & Watch Stream
                Spacer(modifier = Modifier.height(14.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (currentMeta.hasTrailer) {
                        Button(
                            onClick = { launchTrailer(context, currentMeta) },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE50914))
                        ) {
                            Icon(Icons.Default.PlayCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Play Trailer", fontWeight = FontWeight.Bold)
                        }
                    }

                    if (onPlayVod != null) {
                        FilledTonalButton(
                            onClick = onPlayVod,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Play VOD")
                        }
                    } else if (onPlayLive != null) {
                        FilledTonalButton(
                            onClick = onPlayLive,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Tv, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Watch Live")
                        }
                    }
                }

                // Genres
                if (currentMeta.genres.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(14.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        currentMeta.genres.take(4).forEach { g ->
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant
                            ) {
                                Text(
                                    text = g,
                                    fontSize = 11.sp,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                // Description in X language
                Spacer(modifier = Modifier.height(14.dp))
                Text(
                    text = "Description",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = currentMeta.description ?: "No synopsis available from this provider.",
                    style = MaterialTheme.typography.bodyMedium,
                    lineHeight = 22.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )

                // Cast list
                if (currentMeta.cast.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(14.dp))
                    Text(
                        text = "Cast",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = currentMeta.cast.joinToString(", "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Web Link
                if (!currentMeta.webUrl.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(14.dp))
                    OutlinedButton(
                        onClick = {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(currentMeta.webUrl))
                            context.startActivity(intent)
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.OpenInBrowser, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Open on ${activeSource.displayName}")
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

private fun launchTrailer(context: Context, metadata: ProgrammeMetadata) {
    val ytId = metadata.trailerYoutubeId
    val watchUrl = metadata.youtubeWatchUrl ?: metadata.trailerUrl

    if (!ytId.isNullOrBlank()) {
        val appIntent = Intent(Intent.ACTION_VIEW, Uri.parse("vnd.youtube:$ytId"))
        val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/watch?v=$ytId"))
        try {
            context.startActivity(appIntent)
        } catch (_: Exception) {
            context.startActivity(webIntent)
        }
    } else if (!watchUrl.isNullOrBlank()) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(watchUrl))
            context.startActivity(intent)
        } catch (_: Exception) {
            // ignore
        }
    }
}
