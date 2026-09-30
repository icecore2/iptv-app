package com.iptv.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ViewList
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
import com.iptv.app.core.matcher.CatchupResolver
import com.iptv.app.core.model.ChannelWithEpg
import com.iptv.app.core.model.EpgProgramme
import com.iptv.app.core.model.M3uItem
import com.iptv.app.ui.viewmodel.PlaylistViewModel
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

enum class EpgStatusFilter(val label: String) {
    ALL("All Programmes"),
    LIVE_NOW("On Air Now"),
    CATCHUP_VOD("Catchup VOD"),
    UPCOMING("Upcoming")
}

enum class EpgViewLayout {
    BY_CHANNEL,
    FEED
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EpgProgrammesScreen(
    viewModel: PlaylistViewModel,
    onPlayProgrammeVod: (EpgProgramme, M3uItem) -> Unit,
    onPlayChannelLive: (M3uItem) -> Unit,
    onBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val now = remember { System.currentTimeMillis() }
    val timeFormatter = remember {
        DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.systemDefault())
    }

    var isSearchActive by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var selectedStatus by remember { mutableStateOf(EpgStatusFilter.ALL) }
    var selectedCategory by remember { mutableStateOf("All") }
    var viewLayout by remember { mutableStateOf(EpgViewLayout.BY_CHANNEL) }

    // Aggregate all channel + programme schedule pairs
    val channelsWithSchedules: List<Pair<ChannelWithEpg, List<EpgProgramme>>> = remember(
        uiState.channels,
        uiState.epgMatcher,
        selectedCategory,
        selectedStatus,
        searchQuery
    ) {
        val matcher = uiState.epgMatcher ?: return@remember emptyList()
        val query = searchQuery.trim().lowercase()

        uiState.channels.mapNotNull { chItem ->
            if (selectedCategory != "All" && !chItem.channel.group.equals(selectedCategory, ignoreCase = true)) {
                return@mapNotNull null
            }

            val fullSchedule = matcher.getSchedule(chItem.channel)
            if (fullSchedule.isEmpty()) return@mapNotNull null

            val filteredSchedule = fullSchedule.filter { prog ->
                // Filter by status
                val matchesStatus = when (selectedStatus) {
                    EpgStatusFilter.ALL -> true
                    EpgStatusFilter.LIVE_NOW -> prog.isLive(now)
                    EpgStatusFilter.CATCHUP_VOD -> prog.stopEpochMillis < now
                    EpgStatusFilter.UPCOMING -> prog.startEpochMillis > now
                }
                if (!matchesStatus) return@filter false

                // Filter by search query
                if (query.isNotBlank()) {
                    val inTitle = prog.title.lowercase().contains(query)
                    val inDesc = prog.description?.lowercase()?.contains(query) == true
                    val inCat = prog.category?.lowercase()?.contains(query) == true
                    val inChan = chItem.channel.name.lowercase().contains(query)
                    inTitle || inDesc || inCat || inChan
                } else {
                    true
                }
            }

            if (filteredSchedule.isNotEmpty()) {
                chItem to filteredSchedule
            } else {
                null
            }
        }
    }

    // Flat list of all programmes for FEED view
    val allFilteredProgrammes: List<Pair<ChannelWithEpg, EpgProgramme>> = remember(channelsWithSchedules) {
        channelsWithSchedules.flatMap { (channel, progs) ->
            progs.map { channel to it }
        }.sortedBy { it.second.startEpochMillis }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    if (isSearchActive) {
                        TextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = { Text("Search programmes, movies...") },
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
                            Text("EPG Programmes Guide", fontWeight = FontWeight.Bold)
                            Text(
                                "${allFilteredProgrammes.size} programmes across ${channelsWithSchedules.size} channels",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                actions = {
                    // Search toggle
                    IconButton(onClick = {
                        isSearchActive = !isSearchActive
                        if (!isSearchActive) searchQuery = ""
                    }) {
                        Icon(
                            imageVector = if (isSearchActive) Icons.Default.Close else Icons.Default.Search,
                            contentDescription = "Search"
                        )
                    }

                    // View Layout toggle (By Channel vs Feed)
                    IconButton(onClick = {
                        viewLayout = if (viewLayout == EpgViewLayout.BY_CHANNEL) EpgViewLayout.FEED else EpgViewLayout.BY_CHANNEL
                    }) {
                        Icon(
                            imageVector = if (viewLayout == EpgViewLayout.BY_CHANNEL) Icons.AutoMirrored.Filled.ViewList else Icons.Default.CalendarViewMonth,
                            contentDescription = "Toggle Layout Mode"
                        )
                    }

                    // Reload EPG
                    IconButton(onClick = { viewModel.reloadCurrentPlaylist() }) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Reload EPG"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Status Tabs (All, On Air Now, Catchup VOD, Upcoming)
            val statusTabs = listOf(
                EpgStatusFilter.ALL to "All",
                EpgStatusFilter.LIVE_NOW to "On Air Now",
                EpgStatusFilter.CATCHUP_VOD to "Catchup VOD",
                EpgStatusFilter.UPCOMING to "Upcoming"
            )

            PrimaryTabRow(
                selectedTabIndex = statusTabs.indexOfFirst { it.first == selectedStatus }.coerceAtLeast(0),
                containerColor = MaterialTheme.colorScheme.surface
            ) {
                statusTabs.forEachIndexed { _, (status, label) ->
                    Tab(
                        selected = selectedStatus == status,
                        onClick = { selectedStatus = status },
                        text = {
                            Text(
                                label,
                                fontWeight = if (selectedStatus == status) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    )
                }
            }

            // Category Chips Row
            val categories = remember(uiState.categories) { uiState.categories }
            if (categories.size > 1) {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    items(categories) { cat ->
                        FilterChip(
                            selected = selectedCategory.equals(cat, ignoreCase = true),
                            onClick = { selectedCategory = cat },
                            label = { Text(cat) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primary,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                            )
                        )
                    }
                }
            }

            // Content Area
            if (channelsWithSchedules.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CalendarMonth,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(56.dp)
                        )
                        Text(
                            text = if (uiState.epgData == null || uiState.epgData?.programmes?.isEmpty() == true) {
                                "No EPG data loaded for this playlist."
                            } else {
                                "No programmes match the current filters or search."
                            },
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (selectedCategory != "All" || selectedStatus != EpgStatusFilter.ALL || searchQuery.isNotBlank()) {
                            Button(onClick = {
                                selectedCategory = "All"
                                selectedStatus = EpgStatusFilter.ALL
                                searchQuery = ""
                            }) {
                                Icon(Icons.Default.RestartAlt, contentDescription = null)
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Reset Filters")
                            }
                        }
                    }
                }
            } else if (viewLayout == EpgViewLayout.BY_CHANNEL) {
                // By-Channel timeline / schedule view
                LazyColumn(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(channelsWithSchedules, key = { it.first.channel.id + it.first.channel.streamUrl }) { (channelItem, programmes) ->
                        val channel = channelItem.channel

                        Card(
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                // Channel Header
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        if (!channel.logoUrl.isNullOrBlank()) {
                                            AsyncImage(
                                                model = channel.logoUrl,
                                                contentDescription = channel.name,
                                                modifier = Modifier
                                                    .size(38.dp)
                                                    .clip(RoundedCornerShape(8.dp))
                                                    .background(Color.White.copy(alpha = 0.1f)),
                                                contentScale = ContentScale.Fit
                                            )
                                        } else {
                                            Box(
                                                modifier = Modifier
                                                    .size(38.dp)
                                                    .clip(RoundedCornerShape(8.dp))
                                                    .background(MaterialTheme.colorScheme.primaryContainer),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    imageVector = if (channel.isVod) Icons.Default.Movie else Icons.Default.Tv,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.size(22.dp)
                                                )
                                            }
                                        }

                                        Spacer(modifier = Modifier.width(10.dp))

                                        Column {
                                            Text(
                                                text = channel.name,
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.Bold,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text(
                                                text = channel.group,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }

                                    // Watch Live Channel button
                                    FilledTonalButton(
                                        onClick = { onPlayChannelLive(channel) },
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                                    ) {
                                        Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Tune Live", fontSize = 12.sp)
                                    }
                                }

                                Spacer(modifier = Modifier.height(12.dp))

                                // Horizontal Programmes Carousel for this channel
                                LazyRow(
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    contentPadding = PaddingValues(end = 8.dp)
                                ) {
                                    items(programmes, key = { it.title + it.startEpochMillis }) { prog ->
                                        EpgProgrammeTimelineCard(
                                            programme = prog,
                                            channel = channel,
                                            now = now,
                                            timeFormatter = timeFormatter,
                                            onPlayVod = { onPlayProgrammeVod(prog, channel) },
                                            onPlayLive = { onPlayChannelLive(channel) }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                // FEED View: Chronological list of all programmes across all channels
                LazyColumn(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(allFilteredProgrammes, key = { it.first.channel.id + it.second.title + it.second.startEpochMillis }) { (channelItem, prog) ->
                        val channel = channelItem.channel
                        EpgProgrammeFeedCard(
                            programme = prog,
                            channel = channel,
                            now = now,
                            timeFormatter = timeFormatter,
                            onPlayVod = { onPlayProgrammeVod(prog, channel) },
                            onPlayLive = { onPlayChannelLive(channel) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun EpgProgrammeTimelineCard(
    programme: EpgProgramme,
    channel: M3uItem,
    now: Long,
    timeFormatter: DateTimeFormatter,
    onPlayVod: () -> Unit,
    onPlayLive: () -> Unit
) {
    val isLive = programme.isLive(now)
    val isPast = programme.stopEpochMillis < now
    val startTime = timeFormatter.format(Instant.ofEpochMilli(programme.startEpochMillis))
    val stopTime = timeFormatter.format(Instant.ofEpochMilli(programme.stopEpochMillis))

    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isLive)
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
            else
                MaterialTheme.colorScheme.surfaceVariant
        ),
        modifier = Modifier
            .width(220.dp)
            .clickable {
                if (isLive) onPlayLive() else onPlayVod()
            }
    ) {
        Column(
            modifier = Modifier
                .padding(12.dp)
                .fillMaxHeight(),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "$startTime - $stopTime",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = if (isLive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    if (isLive) {
                        Surface(
                            color = MaterialTheme.colorScheme.error,
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                text = "ON AIR",
                                color = Color.White,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                            )
                        }
                    } else if (isPast) {
                        Surface(
                            color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.2f),
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                text = "VOD",
                                color = MaterialTheme.colorScheme.secondary,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = programme.title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                if (!programme.category.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = programme.category,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1
                    )
                }

                if (!programme.description.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = programme.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Action Button
            if (isLive) {
                Button(
                    onClick = onPlayLive,
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Icon(Icons.Default.Tv, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Watch Live", fontSize = 11.sp)
                }
            } else if (isPast) {
                FilledTonalButton(
                    onClick = onPlayVod,
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Play VOD", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            } else {
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Upcoming",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 4.dp),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }
        }
    }
}

@Composable
fun EpgProgrammeFeedCard(
    programme: EpgProgramme,
    channel: M3uItem,
    now: Long,
    timeFormatter: DateTimeFormatter,
    onPlayVod: () -> Unit,
    onPlayLive: () -> Unit
) {
    val isLive = programme.isLive(now)
    val isPast = programme.stopEpochMillis < now
    val startTime = timeFormatter.format(Instant.ofEpochMilli(programme.startEpochMillis))
    val stopTime = timeFormatter.format(Instant.ofEpochMilli(programme.stopEpochMillis))

    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isLive)
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
            else
                MaterialTheme.colorScheme.surfaceVariant
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                if (isLive) onPlayLive() else onPlayVod()
            }
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Header: Channel logo + channel name + status badge
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    if (!channel.logoUrl.isNullOrBlank()) {
                        AsyncImage(
                            model = channel.logoUrl,
                            contentDescription = channel.name,
                            modifier = Modifier
                                .size(28.dp)
                                .clip(RoundedCornerShape(6.dp)),
                            contentScale = ContentScale.Fit
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Text(
                        text = channel.name,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "• $startTime - $stopTime",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (isLive) {
                    Surface(
                        color = MaterialTheme.colorScheme.error,
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            text = "ON AIR",
                            color = Color.White,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                } else if (isPast) {
                    Surface(
                        color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.2f),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            text = "VOD CATCHUP",
                            color = MaterialTheme.colorScheme.secondary,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = programme.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            if (!programme.description.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = programme.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Action Buttons
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (isLive) {
                    Button(
                        onClick = onPlayLive,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Icon(Icons.Default.Tv, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Watch Live", fontSize = 12.sp)
                    }

                    OutlinedButton(
                        onClick = onPlayVod,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Icon(Icons.Default.VideoLibrary, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Play from Start", fontSize = 12.sp)
                    }
                } else if (isPast) {
                    FilledTonalButton(
                        onClick = onPlayVod,
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                        ),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Play VOD Catchup", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }

                if (!programme.category.isNullOrBlank()) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.surface
                    ) {
                        Text(
                            text = programme.category,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }
        }
    }
}
