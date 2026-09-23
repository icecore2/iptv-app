package com.iptv.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.iptv.app.core.matcher.EpgMatcher
import com.iptv.app.core.model.ChannelWithEpg
import com.iptv.app.core.model.EpgData
import com.iptv.app.core.model.EpgProgramme
import com.iptv.app.core.model.M3uItem
import com.iptv.app.core.model.M3uPlaylist
import com.iptv.app.data.EpgRepository
import com.iptv.app.data.PlaylistRepository
import com.iptv.app.data.SampleDataProvider
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

data class PlaylistUiState(
    val isLoading: Boolean = false,
    val error: String? = null,
    val playlist: M3uPlaylist? = null,
    val channels: List<ChannelWithEpg> = emptyList(),
    val categories: List<String> = listOf("All"),
    val selectedCategory: String = "All",
    val searchQuery: String = "",
    val onlyWithEpg: Boolean = false,
    val onlyLiveNow: Boolean = false,
    val sortBy: ChannelSortOrder = ChannelSortOrder.DEFAULT,
    val epgData: EpgData? = null,
    val epgMatcher: EpgMatcher? = null
) {
    val filteredChannels: List<ChannelWithEpg>
        get() {
            var list = channels
            if (selectedCategory != "All") {
                list = list.filter { it.channel.group.equals(selectedCategory, ignoreCase = true) }
            }
            if (onlyWithEpg) {
                list = list.filter { it.currentProgramme != null || it.nextProgramme != null }
            }
            if (onlyLiveNow) {
                list = list.filter { it.currentProgramme != null }
            }
            if (searchQuery.isNotBlank()) {
                val query = searchQuery.trim().lowercase()
                list = list.filter {
                    it.channel.name.lowercase().contains(query) ||
                            (it.currentProgramme?.title?.lowercase()?.contains(query) == true)
                }
            }
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
    private val epgRepository: EpgRepository = EpgRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow(PlaylistUiState())
    val uiState: StateFlow<PlaylistUiState> = _uiState.asStateFlow()

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
                channels = enriched,
                categories = cats.distinct(),
                selectedCategory = "All",
                epgData = sampleEpg,
                epgMatcher = matcher
            )
        }
    }

    fun loadPlaylist(url: String, explicitEpgUrl: String? = null) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
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
                searchQuery = ""
            )
        }
    }

    fun hasActiveFilters(): Boolean {
        val s = _uiState.value
        return s.selectedCategory != "All" || s.onlyWithEpg || s.onlyLiveNow || s.sortBy != ChannelSortOrder.DEFAULT
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
