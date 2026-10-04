package com.iptv.app.ui.screens

import android.view.LayoutInflater
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
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
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.runtime.snapshotFlow
import com.iptv.app.R
import com.iptv.app.core.model.AppSettings
import com.iptv.app.core.model.ChannelWithEpg
import com.iptv.app.core.model.EpgProgramme
import com.iptv.app.core.model.M3uItem
import com.iptv.app.ui.viewmodel.ContentTypeFilter
import com.iptv.app.ui.viewmodel.PlayerViewModel
import com.iptv.app.ui.viewmodel.PlaylistViewModel
import com.iptv.app.ui.viewmodel.SettingsViewModel
import com.iptv.app.ui.viewmodel.ViewMode
import com.iptv.app.ui.components.ProgrammeDetailsBottomSheet
import com.iptv.app.ui.components.ProgrammeDetailsSidePanel
import com.iptv.app.ui.components.SourceBadge
import androidx.compose.ui.platform.LocalConfiguration

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChannelListScreen(
    viewModel: PlaylistViewModel,
    playerViewModel: PlayerViewModel? = null,
    settingsViewModel: SettingsViewModel? = null,
    onChannelSelected: (M3uItem, List<M3uItem>) -> Unit,
    onOpenEpgGuide: () -> Unit = {},
    onPlayProgrammeVod: (EpgProgramme, M3uItem) -> Unit = { _, _ -> },
    onChangePlaylist: () -> Unit,
    onOpenSettings: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()
    val settings = settingsViewModel?.settings?.collectAsState()?.value ?: AppSettings()
    val playerUiState = playerViewModel?.uiState?.collectAsState()?.value
    val channelThumbnails = playerViewModel?.channelThumbnails?.collectAsState()?.value ?: emptyMap()

    var isSearchActive by remember { mutableStateOf(false) }
    var selectedChannelForEpg by remember { mutableStateOf<ChannelWithEpg?>(null) }
    var selectedProgrammeForDetails by remember { mutableStateOf<Pair<EpgProgramme, M3uItem>?>(null) }
    var showFilterDialog by remember { mutableStateOf(false) }
    var showPlaylistSwitcher by remember { mutableStateOf(false) }

    val configuration = LocalConfiguration.current
    val isWideScreen = configuration.screenWidthDp >= 840

    // Pagination state: tracks how many pages of pageSize are loaded
    var pageCount by remember(uiState.filteredChannels, settings.enablePagination, settings.pageSize) {
        mutableIntStateOf(1)
    }

    val displayedChannels = remember(uiState.filteredChannels, pageCount, settings.enablePagination, settings.pageSize) {
        if (settings.enablePagination) {
            uiState.filteredChannels.take(pageCount * settings.pageSize)
        } else {
            uiState.filteredChannels
        }
    }

    val hasMoreToLoad = settings.enablePagination && displayedChannels.size < uiState.filteredChannels.size

    val listState = rememberLazyListState()
    val gridState = rememberLazyGridState()

    // Infinite scroll lazy load detection for list mode
    LaunchedEffect(listState, hasMoreToLoad, displayedChannels.size) {
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index }
            .collect { lastIndex ->
                if (lastIndex != null && hasMoreToLoad && lastIndex >= displayedChannels.size - 6) {
                    pageCount++
                }
            }
    }

    // Infinite scroll lazy load detection for grid mode
    LaunchedEffect(gridState, hasMoreToLoad, displayedChannels.size) {
        snapshotFlow { gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index }
            .collect { lastIndex ->
                if (lastIndex != null && hasMoreToLoad && lastIndex >= displayedChannels.size - 8) {
                    pageCount++
                }
            }
    }

    val rawChannels = remember(uiState.filteredChannels) {
        uiState.filteredChannels.map { it.channel }
    }

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
                        Column(
                            modifier = Modifier.clickable { showPlaylistSwitcher = true }
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("IPTV Player", fontWeight = FontWeight.Bold)
                                Icon(
                                    imageVector = Icons.Default.ArrowDropDown,
                                    contentDescription = "Switch Playlist",
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            val activeName = uiState.activePair?.name ?: "Custom Playlist"
                            val countText = if (settings.enablePagination && displayedChannels.size < uiState.filteredChannels.size) {
                                "${displayedChannels.size} of ${uiState.filteredChannels.size} items"
                            } else {
                                "${uiState.filteredChannels.size} of ${uiState.channels.size} items"
                            }
                            Text(
                                "$activeName • $countText",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
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

                    // Playlist Switcher button
                    IconButton(onClick = { showPlaylistSwitcher = true }) {
                        Icon(
                            imageVector = Icons.Default.SwapHoriz,
                            contentDescription = "Switch Playlist"
                        )
                    }

                    // EPG Programmes Guide
                    IconButton(onClick = onOpenEpgGuide) {
                        Icon(
                            imageVector = Icons.Default.CalendarMonth,
                            contentDescription = "EPG Programmes Guide"
                        )
                    }

                    // Grid / List View Mode toggle
                    IconButton(onClick = { viewModel.toggleViewMode() }) {
                        Icon(
                            imageVector = if (uiState.viewMode == ViewMode.LIST) Icons.Default.GridView else Icons.AutoMirrored.Filled.ViewList,
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

                    // Settings Button
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
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
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
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
                            val isCurrentStreaming = playerViewModel != null &&
                                (playerUiState?.isPlaying == true || playerUiState?.isBuffering == true) &&
                                playerUiState?.currentChannel?.id == ch.id
                            val lastFrame = channelThumbnails[ch.id]

                            Card(
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                                border = if (isCurrentStreaming) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
                                modifier = Modifier
                                    .width(135.dp)
                                    .clickable {
                                        if (isCurrentStreaming) {
                                            onChannelSelected(ch, rawChannels)
                                        } else {
                                            playerViewModel?.captureAndSaveCurrentFrame()
                                            viewModel.addRecentChannel(ch)
                                            onChannelSelected(ch, rawChannels)
                                        }
                                    }
                            ) {
                                Column {
                                    // Small Square Preview Container (110dp)
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(110.dp)
                                            .clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp))
                                            .background(Color.Black),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (isCurrentStreaming) {
                                            var miniViewRef by remember { mutableStateOf<PlayerView?>(null) }
                                            DisposableEffect(miniViewRef) {
                                                onDispose {
                                                    miniViewRef?.let { pv ->
                                                        playerViewModel.unregisterPlayerView(pv)
                                                        pv.player = null
                                                    }
                                                }
                                            }
                                            AndroidView(
                                                factory = { ctx ->
                                                    val pv = LayoutInflater.from(ctx).inflate(R.layout.view_texture_player, null) as PlayerView
                                                    val p = playerViewModel.getOrCreatePlayer(
                                                        ctx,
                                                        settings.bufferDurationSeconds,
                                                        settings.bufferStorageLimitMb
                                                    )
                                                    pv.player = p
                                                    pv.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                                                    playerViewModel.registerPlayerView(pv)
                                                    miniViewRef = pv
                                                    pv
                                                },
                                                update = { pv ->
                                                    val p = playerViewModel.getPlayer()
                                                    if (pv.player != p) {
                                                        pv.player = p
                                                    }
                                                },
                                                modifier = Modifier.fillMaxSize()
                                            )

                                            // LIVE Pill Badge
                                            Box(
                                                modifier = Modifier
                                                    .align(Alignment.TopEnd)
                                                    .padding(6.dp)
                                                    .background(Color(0xFFE53935), RoundedCornerShape(4.dp))
                                                    .padding(horizontal = 5.dp, vertical = 2.dp)
                                            ) {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Box(
                                                        modifier = Modifier
                                                            .size(5.dp)
                                                            .background(Color.White, CircleShape)
                                                    )
                                                    Spacer(modifier = Modifier.width(3.dp))
                                                    Text(
                                                        text = "LIVE",
                                                        color = Color.White,
                                                        style = MaterialTheme.typography.labelSmall.copy(
                                                            fontSize = 9.sp,
                                                            fontWeight = FontWeight.Bold
                                                        )
                                                    )
                                                }
                                            }

                                            if (playerUiState?.isBuffering == true) {
                                                Box(
                                                    modifier = Modifier
                                                        .fillMaxSize()
                                                        .background(Color.Black.copy(alpha = 0.35f)),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    CircularProgressIndicator(
                                                        modifier = Modifier.size(22.dp),
                                                        color = Color.White,
                                                        strokeWidth = 2.dp
                                                    )
                                                }
                                            }
                                        } else if (lastFrame != null) {
                                            // Last Frame Captured as Thumbnail Placeholder!
                                            Image(
                                                bitmap = lastFrame.asImageBitmap(),
                                                contentDescription = ch.name,
                                                modifier = Modifier.fillMaxSize(),
                                                contentScale = ContentScale.Crop
                                            )

                                            // Play icon overlay
                                            Box(
                                                modifier = Modifier
                                                    .size(32.dp)
                                                    .background(Color.Black.copy(alpha = 0.5f), CircleShape),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.PlayArrow,
                                                    contentDescription = null,
                                                    tint = Color.White,
                                                    modifier = Modifier.size(20.dp)
                                                )
                                            }

                                            // If channel has logo, small logo badge in bottom-end
                                            if (!ch.logoUrl.isNullOrBlank()) {
                                                AsyncImage(
                                                    model = ch.logoUrl,
                                                    contentDescription = null,
                                                    modifier = Modifier
                                                        .align(Alignment.BottomEnd)
                                                        .padding(4.dp)
                                                        .size(20.dp)
                                                        .clip(RoundedCornerShape(3.dp))
                                                        .background(Color.Black.copy(alpha = 0.6f)),
                                                    contentScale = ContentScale.Fit
                                                )
                                            }
                                        } else {
                                            // Fallback Logo / Icon placeholder
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxSize()
                                                    .background(MaterialTheme.colorScheme.surfaceVariant),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                if (!ch.logoUrl.isNullOrBlank()) {
                                                    AsyncImage(
                                                        model = ch.logoUrl,
                                                        contentDescription = ch.name,
                                                        modifier = Modifier
                                                            .size(44.dp)
                                                            .clip(RoundedCornerShape(6.dp)),
                                                        contentScale = ContentScale.Fit
                                                    )
                                                } else {
                                                    Icon(
                                                        imageVector = if (ch.isVod) Icons.Default.Movie else Icons.Default.Tv,
                                                        contentDescription = null,
                                                        tint = MaterialTheme.colorScheme.primary,
                                                        modifier = Modifier.size(44.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }

                                    // Channel Info Below Square
                                    Column(
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                                    ) {
                                        Text(
                                            text = ch.name,
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = FontWeight.SemiBold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = if (isCurrentStreaming) "Now Streaming" else ch.group,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = if (isCurrentStreaming) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                            fontWeight = if (isCurrentStreaming) FontWeight.Bold else FontWeight.Normal,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
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
                        state = listState,
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 48.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(displayedChannels, key = { it.channel.id + it.channel.streamUrl }) { item ->
                            ChannelCard(
                                channelWithEpg = item,
                                isFavorite = viewModel.isFavorite(item.channel.id),
                                showLogo = settings.showChannelLogos,
                                showEpg = settings.showEpgInList,
                                showMetadataBadge = settings.showInlineMetadataBadge && uiState.epgMatcher != null,
                                metadataSource = settings.preferredMetadataSource,
                                onProgrammeMetadataClick = { prog -> selectedProgrammeForDetails = prog to item.channel },
                                onToggleFavorite = { viewModel.toggleFavorite(item.channel.id) },
                                onClick = {
                                    viewModel.addRecentChannel(item.channel)
                                    onChannelSelected(item.channel, rawChannels)
                                },
                                onEpgClick = { selectedChannelForEpg = item }
                            )
                        }

                        if (hasMoreToLoad) {
                            item {
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { pageCount++ }
                                        .padding(vertical = 6.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(14.dp),
                                        horizontalArrangement = Arrangement.Center,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Text(
                                            text = "Loading more channels (${displayedChannels.size} of ${uiState.filteredChannels.size})...",
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                }
                            }
                        }
                    }
                } else {
                    // Grid View (2 Columns)
                    LazyVerticalGrid(
                        state = gridState,
                        columns = GridCells.Fixed(2),
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 48.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(displayedChannels, key = { "grid_" + it.channel.id + it.channel.streamUrl }) { item ->
                            ChannelGridCard(
                                channelWithEpg = item,
                                isFavorite = viewModel.isFavorite(item.channel.id),
                                showLogo = settings.showChannelLogos,
                                showEpg = settings.showEpgInList,
                                showMetadataBadge = settings.showInlineMetadataBadge && uiState.epgMatcher != null,
                                metadataSource = settings.preferredMetadataSource,
                                onProgrammeMetadataClick = { prog -> selectedProgrammeForDetails = prog to item.channel },
                                onToggleFavorite = { viewModel.toggleFavorite(item.channel.id) },
                                onClick = {
                                    viewModel.addRecentChannel(item.channel)
                                    onChannelSelected(item.channel, rawChannels)
                                },
                                onEpgClick = { selectedChannelForEpg = item }
                            )
                        }

                        if (hasMoreToLoad) {
                            item(span = { GridItemSpan(2) }) {
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { pageCount++ }
                                        .padding(vertical = 6.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(14.dp),
                                        horizontalArrangement = Arrangement.Center,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Text(
                                            text = "Loading more channels (${displayedChannels.size} of ${uiState.filteredChannels.size})...",
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        if (isWideScreen && selectedProgrammeForDetails != null) {
                val (prog, ch) = selectedProgrammeForDetails!!
                ProgrammeDetailsSidePanel(
                    programmeTitle = prog.title,
                    channelName = ch.name,
                    metadataRepository = viewModel.metadataRepository,
                    preferredLanguage = settings.metadataLanguage,
                    preferredSource = settings.preferredMetadataSource,
                    onPlayLive = {
                        viewModel.addRecentChannel(ch)
                        onChannelSelected(ch, rawChannels)
                    },
                    onPlayVod = {
                        onPlayProgrammeVod(prog, ch)
                    },
                    onClose = { selectedProgrammeForDetails = null }
                )
            }
        }
    }

    // Programme Details Bottom Sheet (compact/portrait)
    if (!isWideScreen && selectedProgrammeForDetails != null) {
        val (prog, ch) = selectedProgrammeForDetails!!
        ProgrammeDetailsBottomSheet(
            programmeTitle = prog.title,
            channelName = ch.name,
            metadataRepository = viewModel.metadataRepository,
            preferredLanguage = settings.metadataLanguage,
            preferredSource = settings.preferredMetadataSource,
            onPlayLive = {
                selectedProgrammeForDetails = null
                viewModel.addRecentChannel(ch)
                onChannelSelected(ch, rawChannels)
            },
            onPlayVod = {
                selectedProgrammeForDetails = null
                onPlayProgrammeVod(prog, ch)
            },
            onDismiss = { selectedProgrammeForDetails = null }
        )
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
            showMetadataBadge = settings.showInlineMetadataBadge && uiState.epgMatcher != null,
            metadataSource = settings.preferredMetadataSource,
            onProgrammeMetadataClick = { prog -> selectedProgrammeForDetails = prog to channelItem.channel },
            onPlayProgrammeVod = onPlayProgrammeVod,
            onPlayChannelLive = { ch -> onChannelSelected(ch, rawChannels) },
            onDismiss = { selectedChannelForEpg = null }
        )
    }

    // Playlist Switcher Bottom Sheet
    if (showPlaylistSwitcher) {
        PlaylistSwitcherSheet(
            viewModel = viewModel,
            onDismiss = { showPlaylistSwitcher = false },
            onNavigateToInput = {
                showPlaylistSwitcher = false
                onChangePlaylist()
            }
        )
    }
}

@Composable
fun ChannelCard(
    channelWithEpg: ChannelWithEpg,
    isFavorite: Boolean,
    showLogo: Boolean = true,
    showEpg: Boolean = true,
    showMetadataBadge: Boolean = false,
    metadataSource: com.iptv.app.core.metadata.MetadataSource = com.iptv.app.core.metadata.MetadataSource.AUTO,
    onProgrammeMetadataClick: ((EpgProgramme) -> Unit)? = null,
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
                if (showLogo && !channel.logoUrl.isNullOrBlank()) {
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
                        if (channel.name.isNotBlank()) {
                            Text(
                                text = channel.name.take(2).uppercase(),
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                fontSize = 18.sp
                            )
                        } else {
                            Icon(
                                imageVector = if (channel.isVod) Icons.Default.Movie else Icons.Default.Tv,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
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

                    if (showEpg && currentProg != null) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "Now: ${currentProg.title}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false)
                            )
                            if (showMetadataBadge && onProgrammeMetadataClick != null) {
                                Spacer(modifier = Modifier.width(6.dp))
                                SourceBadge(
                                    source = metadataSource,
                                    onClick = { onProgrammeMetadataClick(currentProg) }
                                )
                            }
                        }

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

                    if (showEpg && channelWithEpg.nextProgramme != null) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "Next: ${channelWithEpg.nextProgramme.title}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false)
                            )
                            if (showMetadataBadge && onProgrammeMetadataClick != null) {
                                Spacer(modifier = Modifier.width(6.dp))
                                SourceBadge(
                                    source = metadataSource,
                                    onClick = { onProgrammeMetadataClick(channelWithEpg.nextProgramme) }
                                )
                            }
                        }
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
    showLogo: Boolean = true,
    showEpg: Boolean = true,
    showMetadataBadge: Boolean = false,
    metadataSource: com.iptv.app.core.metadata.MetadataSource = com.iptv.app.core.metadata.MetadataSource.AUTO,
    onProgrammeMetadataClick: ((EpgProgramme) -> Unit)? = null,
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
                if (showLogo && !channel.logoUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = channel.logoUrl,
                        contentDescription = channel.name,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(8.dp),
                        contentScale = ContentScale.Fit
                    )
                } else {
                    if (channel.name.isNotBlank()) {
                        Text(
                            text = channel.name.take(2).uppercase(),
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            fontSize = 24.sp
                        )
                    } else {
                        Icon(
                            imageVector = if (channel.isVod) Icons.Default.Movie else Icons.Default.Tv,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(44.dp)
                        )
                    }
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

            if (showEpg && currentProg != null) {
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = currentProg.title,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (showMetadataBadge && onProgrammeMetadataClick != null) {
                        Spacer(modifier = Modifier.width(4.dp))
                        SourceBadge(
                            source = metadataSource,
                            onClick = { onProgrammeMetadataClick(currentProg) }
                        )
                    }
                }
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
