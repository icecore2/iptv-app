package com.iptv.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.iptv.app.core.model.ChannelWithEpg
import com.iptv.app.core.model.M3uItem
import com.iptv.app.ui.viewmodel.ContentTypeFilter
import com.iptv.app.ui.viewmodel.PlaylistViewModel
import com.iptv.app.ui.viewmodel.ViewMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChannelListScreen(
    viewModel: PlaylistViewModel,
    onChannelSelected: (M3uItem, List<M3uItem>) -> Unit,
    onChangePlaylist: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    var isSearchActive by remember { mutableStateOf(false) }
    var selectedChannelForEpg by remember { mutableStateOf<ChannelWithEpg?>(null) }
    var showFilterDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    if (isSearchActive) {
                        TextField(
                            value = uiState.searchQuery,
                            onValueChange = { viewModel.updateSearchQuery(it) },
                            placeholder = { Text("Search channels, movies, EPG...") },
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent
                            ),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    } else {
                        Column {
                            Text("IPTV Player", fontWeight = FontWeight.Bold)
                            Text(
                                "${uiState.filteredChannels.size} of ${uiState.channels.size} items",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                actions = {
                    // Search toggle
                    IconButton(onClick = {
                        isSearchActive = !isSearchActive
                        if (!isSearchActive) viewModel.updateSearchQuery("")
                    }) {
                        Icon(
                            imageVector = if (isSearchActive) Icons.Default.Close else Icons.Default.Search,
                            contentDescription = "Search"
                        )
                    }

                    // Grid / List View Mode toggle
                    IconButton(onClick = { viewModel.toggleViewMode() }) {
                        Icon(
                            imageVector = if (uiState.viewMode == ViewMode.LIST) Icons.Default.GridView else Icons.Default.ViewList,
                            contentDescription = "Toggle View Mode"
                        )
                    }

                    // Filter Action Button with badge
                    IconButton(onClick = { showFilterDialog = true }) {
                        BadgedBox(
                            badge = {
                                if (viewModel.hasActiveFilters()) {
                                    Badge(containerColor = MaterialTheme.colorScheme.secondary) {
                                        Text("!")
                                    }
                                }
                            }
                        ) {
                            Icon(Icons.Default.FilterList, contentDescription = "Filter Channels")
                        }
                    }

                    // Quick Refresh / Reload Playlist
                    IconButton(onClick = { viewModel.reloadCurrentPlaylist() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Reload Playlist")
                    }

                    // Change / Load Playlist Action Button
                    IconButton(onClick = onChangePlaylist) {
                        Icon(Icons.Default.FolderOpen, contentDescription = "Change Playlist")
                    }
                },
                windowInsets = TopAppBarDefaults.windowInsets,
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        contentWindowInsets = ScaffoldDefaults.contentWindowInsets
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Content Type Tabs (All, Live TV, Movies / VOD, Favorites)
            val contentTabs = listOf(
                ContentTypeFilter.ALL to "All",
                ContentTypeFilter.LIVE_TV to "Live TV",
                ContentTypeFilter.VOD to "Movies / VOD",
                ContentTypeFilter.FAVORITES to "Favorites (${uiState.favoriteIds.size})"
            )

            PrimaryTabRow(
                selectedTabIndex = contentTabs.indexOfFirst { it.first == uiState.contentType }.coerceAtLeast(0),
                containerColor = MaterialTheme.colorScheme.surface
            ) {
                contentTabs.forEachIndexed { index, (type, label) ->
                    Tab(
                        selected = uiState.contentType == type,
                        onClick = { viewModel.setContentType(type) },
                        text = {
                            Text(label, fontWeight = if (uiState.contentType == type) FontWeight.Bold else FontWeight.Normal)
                        }
                    )
                }
            }

            // Category Chips Row with leading Filters button
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                item {
                    FilterChip(
                        selected = viewModel.hasActiveFilters(),
                        onClick = { showFilterDialog = true },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Tune,
                                contentDescription = "Open Filters",
                                modifier = Modifier.size(16.dp)
                            )
                        },
                        label = { Text("Filters") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    )
                }

                items(uiState.categories) { category ->
                    FilterChip(
                        selected = uiState.selectedCategory.equals(category, ignoreCase = true),
                        onClick = { viewModel.selectCategory(category) },
                        label = { Text(category) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primary,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                        )
                    )
                }
            }

            val rawChannels = remember(uiState.filteredChannels) {
                uiState.filteredChannels.map { it.channel }
            }

            // Recently Watched Shelf
            if (uiState.recentChannels.isNotEmpty() && uiState.searchQuery.isBlank() && uiState.contentType == ContentTypeFilter.ALL) {
                Column(modifier = Modifier.padding(top = 4.dp, bottom = 8.dp)) {
                    Text(
                        text = "Recently Watched",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                    )

                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(uiState.recentChannels, key = { "recent_" + it.id }) { ch ->
                            Card(
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                                modifier = Modifier
                                    .width(130.dp)
                                    .clickable {
                                        viewModel.addRecentChannel(ch)
                                        onChannelSelected(ch, rawChannels)
                                    }
                            ) {
                                Column(
                                    modifier = Modifier.padding(8.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    if (!ch.logoUrl.isNullOrBlank()) {
                                        AsyncImage(
                                            model = ch.logoUrl,
                                            contentDescription = ch.name,
                                            modifier = Modifier
                                                .size(40.dp)
                                                .clip(RoundedCornerShape(6.dp)),
                                            contentScale = ContentScale.Fit
                                        )
                                    } else {
                                        Icon(
                                            imageVector = if (ch.isVod) Icons.Default.Movie else Icons.Default.Tv,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(40.dp)
                                        )
                                    }

                                    Spacer(modifier = Modifier.height(6.dp))

                                    Text(
                                        text = ch.name,
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Empty State
            if (uiState.filteredChannels.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = when {
                                uiState.contentType == ContentTypeFilter.FAVORITES -> "No favorite channels yet. Tap the heart icon to add favorites!"
                                uiState.searchQuery.isNotBlank() -> "No channels matching '${uiState.searchQuery}'"
                                else -> "No channels match current filters."
                            },
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (viewModel.hasActiveFilters()) {
                            TextButton(onClick = { viewModel.resetFilters() }) {
                                Icon(Icons.Default.RestartAlt, contentDescription = null)
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Reset Filters")
                            }
                        }
                    }
                }
            } else {
                // View Mode: List View or Grid View
                if (uiState.viewMode == ViewMode.LIST) {
                    LazyColumn(
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 48.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(uiState.filteredChannels, key = { it.channel.id + it.channel.streamUrl }) { item ->
                            ChannelCard(
                                channelWithEpg = item,
                                isFavorite = viewModel.isFavorite(item.channel.id),
                                onToggleFavorite = { viewModel.toggleFavorite(item.channel.id) },
                                onClick = {
                                    viewModel.addRecentChannel(item.channel)
                                    onChannelSelected(item.channel, rawChannels)
                                },
                                onEpgClick = { selectedChannelForEpg = item }
                            )
                        }
                    }
                } else {
                    // Grid View (2 Columns)
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 48.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(uiState.filteredChannels, key = { "grid_" + it.channel.id + it.channel.streamUrl }) { item ->
                            ChannelGridCard(
                                channelWithEpg = item,
                                isFavorite = viewModel.isFavorite(item.channel.id),
                                onToggleFavorite = { viewModel.toggleFavorite(item.channel.id) },
                                onClick = {
                                    viewModel.addRecentChannel(item.channel)
                                    onChannelSelected(item.channel, rawChannels)
                                },
                                onEpgClick = { selectedChannelForEpg = item }
                            )
                        }
                    }
                }
            }
        }
    }

    // Interactive Filter Dialog
    if (showFilterDialog) {
        ChannelFilterDialog(
            viewModel = viewModel,
            onDismiss = { showFilterDialog = false }
        )
    }

    // EPG Schedule Bottom Sheet
    selectedChannelForEpg?.let { channelItem ->
        val schedule = viewModel.getChannelSchedule(channelItem.channel)
        EpgScheduleSheet(
            channelWithEpg = channelItem,
            schedule = schedule,
            onDismiss = { selectedChannelForEpg = null }
        )
    }
}

@Composable
fun ChannelCard(
    channelWithEpg: ChannelWithEpg,
    isFavorite: Boolean,
    onToggleFavorite: () -> Unit,
    onClick: () -> Unit,
    onEpgClick: () -> Unit
) {
    val channel = channelWithEpg.channel
    val currentProg = channelWithEpg.currentProgramme

    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                // Channel Logo
                if (!channel.logoUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = channel.logoUrl,
                        contentDescription = channel.name,
                        modifier = Modifier
                            .size(50.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color.White.copy(alpha = 0.1f)),
                        contentScale = ContentScale.Fit
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(50.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (channel.isVod) Icons.Default.Movie else Icons.Default.Tv,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                // Channel details
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = channel.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = MaterialTheme.colorScheme.surface
                        ) {
                            Text(
                                text = channel.group,
                                fontSize = 10.sp,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    if (currentProg != null) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Now: ${currentProg.title}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )

                        Spacer(modifier = Modifier.height(4.dp))
                        LinearProgressIndicator(
                            progress = { channelWithEpg.progress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(4.dp)
                                .clip(RoundedCornerShape(2.dp)),
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f)
                        )
                    }

                    if (channelWithEpg.nextProgramme != null) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Next: ${channelWithEpg.nextProgramme.title}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Spacer(modifier = Modifier.width(4.dp))

                // Favorite Button
                IconButton(onClick = onToggleFavorite, modifier = Modifier.size(36.dp)) {
                    Icon(
                        imageVector = if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        contentDescription = "Favorite",
                        tint = if (isFavorite) Color(0xFFFF4757) else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }

                // Guide button
                IconButton(onClick = onEpgClick, modifier = Modifier.size(36.dp)) {
                    Icon(
                        imageVector = Icons.Default.CalendarMonth,
                        contentDescription = "EPG Schedule",
                        tint = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.size(20.dp)
                    )
                }

                // Play icon
                FilledIconButton(
                    onClick = onClick,
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    ),
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "Play Channel",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun ChannelGridCard(
    channelWithEpg: ChannelWithEpg,
    isFavorite: Boolean,
    onToggleFavorite: () -> Unit,
    onClick: () -> Unit,
    onEpgClick: () -> Unit
) {
    val channel = channelWithEpg.channel
    val currentProg = channelWithEpg.currentProgramme

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(80.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color.White.copy(alpha = 0.08f)),
                contentAlignment = Alignment.Center
            ) {
                if (!channel.logoUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = channel.logoUrl,
                        contentDescription = channel.name,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(8.dp),
                        contentScale = ContentScale.Fit
                    )
                } else {
                    Icon(
                        imageVector = if (channel.isVod) Icons.Default.Movie else Icons.Default.Tv,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(44.dp)
                    )
                }

                // Favorite icon overlay top-right
                IconButton(
                    onClick = onToggleFavorite,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .size(30.dp)
                ) {
                    Icon(
                        imageVector = if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        contentDescription = "Favorite",
                        tint = if (isFavorite) Color(0xFFFF4757) else Color.White.copy(alpha = 0.7f),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = channel.name,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Text(
                text = channel.group,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )

            if (currentProg != null) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = currentProg.title,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                LinearProgressIndicator(
                    progress = { channelWithEpg.progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .clip(RoundedCornerShape(2.dp)),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f)
                )
            }
        }
    }
}
