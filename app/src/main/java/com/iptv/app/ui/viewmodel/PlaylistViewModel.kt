package com.iptv.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.iptv.app.core.matcher.EpgMatcher
import com.iptv.app.core.model.ChannelWithEpg
import com.iptv.app.core.model.EpgData
import com.iptv.app.core.model.EpgProgramme
import com.iptv.app.core.model.M3uItem
import com.iptv.app.core.model.M3uPlaylist
import com.iptv.app.core.model.SavedPlaylistPair
import com.iptv.app.data.DEFAULT_SAMPLE_PAIR
import com.iptv.app.data.EpgRepository
import com.iptv.app.data.InMemorySavedPlaylistRepository
import com.iptv.app.data.PlaylistRepository
import com.iptv.app.data.SampleDataProvider
import com.iptv.app.data.SavedPlaylistRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class ChannelSortOrder(val label: String) {
    DEFAULT("Default Playlist Order"),
    NAME_ASC("Name: A to Z"),
    NAME_DESC("Name: Z to A"),
    GROUP("Group / Category")
}

enum class ContentTypeFilter(val label: String) {
    ALL("All Channels"),
    LIVE_TV("Live TV"),
    VOD("Movies & VOD"),
    FAVORITES("Favorites")
}

enum class ViewMode {
    LIST,
    GRID
}

data class PlaylistUiState(
    val isLoading: Boolean = false,
    val error: String? = null,
    val playlist: M3uPlaylist? = null,
    val currentUrl: String? = null,
    val currentEpgUrl: String? = null,
    val channels: List<ChannelWithEpg> = emptyList(),
    val categories: List<String> = listOf("All"),
    val selectedCategory: String = "All",
    val searchQuery: String = "",
    val onlyWithEpg: Boolean = false,
    val onlyLiveNow: Boolean = false,
    val sortBy: ChannelSortOrder = ChannelSortOrder.DEFAULT,
    val contentType: ContentTypeFilter = ContentTypeFilter.ALL,
    val viewMode: ViewMode = ViewMode.LIST,
    val favoriteIds: Set<String> = emptySet(),
    val recentChannels: List<M3uItem> = emptyList(),
    val epgData: EpgData? = null,
    val epgMatcher: EpgMatcher? = null,
    val savedPlaylists: List<SavedPlaylistPair> = emptyList(),
    val activePairId: String? = null
) {
    val activePair: SavedPlaylistPair?
        get() = savedPlaylists.find { it.id == activePairId }

    val filteredChannels: List<ChannelWithEpg>
        get() {
            var list = channels

            // Filter by Content Type (Live TV vs VOD vs Favorites)
            when (contentType) {
                ContentTypeFilter.ALL -> {}
                ContentTypeFilter.LIVE_TV -> list = list.filter { !it.channel.isVod }
                ContentTypeFilter.VOD -> list = list.filter { it.channel.isVod }
                ContentTypeFilter.FAVORITES -> list = list.filter { favoriteIds.contains(it.channel.id) }
            }

            // Filter by Category
            if (selectedCategory != "All") {
                list = list.filter { it.channel.group.equals(selectedCategory, ignoreCase = true) }
            }

            // Toggles
            if (onlyWithEpg) {
                list = list.filter { it.currentProgramme != null || it.nextProgramme != null }
            }
            if (onlyLiveNow) {
                list = list.filter { it.currentProgramme != null }
            }

            // Search query
            if (searchQuery.isNotBlank()) {
                val query = searchQuery.trim().lowercase()
                list = list.filter {
                    it.channel.name.lowercase().contains(query) ||
                            (it.currentProgramme?.title?.lowercase()?.contains(query) == true)
                }
            }

            // Sorting
            return when (sortBy) {
                ChannelSortOrder.DEFAULT -> list
                ChannelSortOrder.NAME_ASC -> list.sortedBy { it.channel.name.lowercase() }
                ChannelSortOrder.NAME_DESC -> list.sortedByDescending { it.channel.name.lowercase() }
                ChannelSortOrder.GROUP -> list.sortedWith(compareBy({ it.channel.group.lowercase() }, { it.channel.name.lowercase() }))
            }
        }
}

class PlaylistViewModel(
    private val playlistRepository: PlaylistRepository = PlaylistRepository(),
    private val epgRepository: EpgRepository = EpgRepository(),
    private val savedPlaylistRepository: SavedPlaylistRepository = InMemorySavedPlaylistRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow(PlaylistUiState())
    val uiState: StateFlow<PlaylistUiState> = _uiState.asStateFlow()

    init {
        loadSavedPlaylists()
    }

    fun loadSavedPlaylists(autoLoadActive: Boolean = false) {
        viewModelScope.launch {
            val list = savedPlaylistRepository.getSavedPlaylists()
            val activeId = savedPlaylistRepository.getActivePairId()
            _uiState.update {
                it.copy(
                    savedPlaylists = list,
                    activePairId = activeId ?: it.activePairId
                )
            }
            if (autoLoadActive && activeId != null && _uiState.value.channels.isEmpty()) {
                list.find { it.id == activeId }?.let { pair ->
                    switchToPlaylist(pair)
                }
            }
        }
    }

    fun switchToPlaylist(pair: SavedPlaylistPair) {
        viewModelScope.launch {
            savedPlaylistRepository.setActivePairId(pair.id)
            _uiState.update {
                it.copy(
                    activePairId = pair.id,
                    favoriteIds = pair.favoriteIds
                )
            }
            if (pair.isSample) {
                loadSampleData()
            } else {
                loadPlaylist(pair.playlistUrl, pair.epgUrl)
            }
        }
    }

    fun saveAndSwitch(name: String, playlistUrl: String, epgUrl: String?) {
        viewModelScope.launch {
            val newPair = SavedPlaylistPair(
                name = name.trim(),
                playlistUrl = playlistUrl.trim(),
                epgUrl = epgUrl?.trim()?.ifBlank { null }
            )
            savedPlaylistRepository.savePlaylist(newPair)
            val list = savedPlaylistRepository.getSavedPlaylists()
            _uiState.update { it.copy(savedPlaylists = list) }
            switchToPlaylist(newPair)
        }
    }

    fun savePlaylist(name: String, playlistUrl: String, epgUrl: String?) {
        viewModelScope.launch {
            val newPair = SavedPlaylistPair(
                name = name.trim(),
                playlistUrl = playlistUrl.trim(),
                epgUrl = epgUrl?.trim()?.ifBlank { null }
            )
            savedPlaylistRepository.savePlaylist(newPair)
            val list = savedPlaylistRepository.getSavedPlaylists()
            _uiState.update { it.copy(savedPlaylists = list) }
        }
    }

    fun updatePlaylist(pair: SavedPlaylistPair) {
        viewModelScope.launch {
            savedPlaylistRepository.updatePlaylist(pair)
            val list = savedPlaylistRepository.getSavedPlaylists()
            _uiState.update { it.copy(savedPlaylists = list) }

            // If updating currently active pair, reload if URLs changed
            val current = _uiState.value
            if (current.activePairId == pair.id) {
                if (pair.isSample) {
                    loadSampleData()
                } else if (current.currentUrl != pair.playlistUrl || current.currentEpgUrl != pair.epgUrl) {
                    loadPlaylist(pair.playlistUrl, pair.epgUrl)
                }
            }
        }
    }

    fun deletePlaylist(id: String) {
        viewModelScope.launch {
            savedPlaylistRepository.deletePlaylist(id)
            val wasActive = _uiState.value.activePairId == id
            val list = savedPlaylistRepository.getSavedPlaylists()
            _uiState.update { it.copy(savedPlaylists = list) }

            if (wasActive) {
                if (list.isNotEmpty()) {
                    switchToPlaylist(list.first())
                } else {
                    clearPlaylist()
                    _uiState.update { it.copy(activePairId = null) }
                }
            }
        }
    }

    fun loadSampleData() {
        val samplePlaylist = SampleDataProvider.getSamplePlaylist()
        val sampleEpg = SampleDataProvider.getSampleEpg()
        val matcher = EpgMatcher(sampleEpg)
        val enriched = samplePlaylist.items.map { matcher.enrichChannel(it) }

        val cats = mutableListOf("All")
        cats.addAll(samplePlaylist.groups)

        _uiState.update {
            it.copy(
                isLoading = false,
                error = null,
                playlist = samplePlaylist,
                currentUrl = null,
                currentEpgUrl = null,
                channels = enriched,
                categories = cats.distinct(),
                selectedCategory = "All",
                epgData = sampleEpg,
                epgMatcher = matcher,
                activePairId = it.activePairId ?: DEFAULT_SAMPLE_PAIR.id
            )
        }
    }

    fun clearPlaylist() {
        _uiState.update {
            it.copy(
                isLoading = false,
                error = null,
                playlist = null,
                currentUrl = null,
                currentEpgUrl = null,
                channels = emptyList(),
                categories = listOf("All"),
                selectedCategory = "All",
                searchQuery = "",
                onlyWithEpg = false,
                onlyLiveNow = false,
                sortBy = ChannelSortOrder.DEFAULT,
                contentType = ContentTypeFilter.ALL,
                epgData = null,
                epgMatcher = null
            )
        }
    }

    fun loadPlaylist(url: String, explicitEpgUrl: String? = null) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null, currentUrl = url, currentEpgUrl = explicitEpgUrl) }
            val result = playlistRepository.loadPlaylistFromUrl(url)
            result.onSuccess { playlist ->
                val cats = mutableListOf("All")
                cats.addAll(playlist.groups)

                val currentMatcher = _uiState.value.epgMatcher
                val enriched = playlist.items.map {
                    currentMatcher?.enrichChannel(it) ?: ChannelWithEpg(channel = it)
                }

                _uiState.update {
                    it.copy(
                        isLoading = false,
                        playlist = playlist,
                        channels = enriched,
                        categories = cats.distinct(),
                        selectedCategory = "All"
                    )
                }

                val targetEpgUrl = explicitEpgUrl ?: playlist.epgUrl
                if (!targetEpgUrl.isNullOrBlank()) {
                    loadEpg(targetEpgUrl)
                }
            }.onFailure { err ->
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = "Failed to load playlist: ${err.localizedMessage ?: err.message}"
                    )
                }
            }
        }
    }

    fun reloadCurrentPlaylist() {
        val activePair = _uiState.value.activePair
        if (activePair != null) {
            switchToPlaylist(activePair)
            return
        }

        val currentUrl = _uiState.value.currentUrl
        val currentEpg = _uiState.value.currentEpgUrl
        if (currentUrl != null) {
            loadPlaylist(currentUrl, currentEpg)
        } else {
            loadSampleData()
        }
    }

    fun loadEpg(epgUrl: String) {
        viewModelScope.launch {
            val result = epgRepository.loadEpgFromUrl(epgUrl)
            result.onSuccess { epgData ->
                val matcher = EpgMatcher(epgData)
                val currentPlaylist = _uiState.value.playlist
                val enriched = currentPlaylist?.items?.map { matcher.enrichChannel(it) }
                    ?: _uiState.value.channels.map { matcher.enrichChannel(it.channel) }

                _uiState.update {
                    it.copy(
                        epgData = epgData,
                        epgMatcher = matcher,
                        channels = enriched
                    )
                }
            }.onFailure { _ ->
                // Keep channels intact on EPG failure
            }
        }
    }

    fun selectCategory(category: String) {
        _uiState.update { it.copy(selectedCategory = category) }
    }

    fun updateSearchQuery(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
    }

    fun setContentType(type: ContentTypeFilter) {
        _uiState.update { it.copy(contentType = type) }
    }

    fun toggleViewMode() {
        _uiState.update {
            it.copy(viewMode = if (it.viewMode == ViewMode.LIST) ViewMode.GRID else ViewMode.LIST)
        }
    }

    fun toggleFavorite(channelId: String) {
        _uiState.update { current ->
            val set = current.favoriteIds.toMutableSet()
            if (set.contains(channelId)) {
                set.remove(channelId)
            } else {
                set.add(channelId)
            }
            current.copy(favoriteIds = set)
        }

        val activeId = _uiState.value.activePairId
        if (activeId != null) {
            viewModelScope.launch {
                val newFavorites = _uiState.value.favoriteIds
                savedPlaylistRepository.updateFavorites(activeId, newFavorites)
                _uiState.update { state ->
                    val updatedList = state.savedPlaylists.map {
                        if (it.id == activeId) it.copy(favoriteIds = newFavorites) else it
                    }
                    state.copy(savedPlaylists = updatedList)
                }
            }
        }
    }

    fun isFavorite(channelId: String): Boolean {
        return _uiState.value.favoriteIds.contains(channelId)
    }

    fun addRecentChannel(channel: M3uItem) {
        _uiState.update { current ->
            val existing = current.recentChannels.filterNot { it.id == channel.id }.toMutableList()
            existing.add(0, channel) // Push to top
            current.copy(recentChannels = existing.take(12))
        }
    }

    fun setFilterOptions(
        category: String? = null,
        onlyWithEpg: Boolean? = null,
        onlyLiveNow: Boolean? = null,
        sortBy: ChannelSortOrder? = null
    ) {
        _uiState.update { current ->
            current.copy(
                selectedCategory = category ?: current.selectedCategory,
                onlyWithEpg = onlyWithEpg ?: current.onlyWithEpg,
                onlyLiveNow = onlyLiveNow ?: current.onlyLiveNow,
                sortBy = sortBy ?: current.sortBy
            )
        }
    }

    fun resetFilters() {
        _uiState.update {
            it.copy(
                selectedCategory = "All",
                onlyWithEpg = false,
                onlyLiveNow = false,
                sortBy = ChannelSortOrder.DEFAULT,
                contentType = ContentTypeFilter.ALL,
                searchQuery = ""
            )
        }
    }

    fun hasActiveFilters(): Boolean {
        val s = _uiState.value
        return s.selectedCategory != "All" || s.onlyWithEpg || s.onlyLiveNow ||
                s.sortBy != ChannelSortOrder.DEFAULT || s.contentType != ContentTypeFilter.ALL
    }

    fun getCategoryCounts(): Map<String, Int> {
        val channels = _uiState.value.channels
        val counts = mutableMapOf<String, Int>()
        counts["All"] = channels.size
        for (item in channels) {
            val group = item.channel.group
            counts[group] = (counts[group] ?: 0) + 1
        }
        return counts
    }

    fun getChannelSchedule(channel: M3uItem): List<EpgProgramme> {
        return _uiState.value.epgMatcher?.getSchedule(channel) ?: emptyList()
    }
}
