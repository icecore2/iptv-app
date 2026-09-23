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

data class PlaylistUiState(
    val isLoading: Boolean = false,
    val error: String? = null,
    val playlist: M3uPlaylist? = null,
    val channels: List<ChannelWithEpg> = emptyList(),
    val categories: List<String> = listOf("All"),
    val selectedCategory: String = "All",
    val searchQuery: String = "",
    val epgData: EpgData? = null,
    val epgMatcher: EpgMatcher? = null
) {
    val filteredChannels: List<ChannelWithEpg>
        get() {
            var list = channels
            if (selectedCategory != "All") {
                list = list.filter { it.channel.group.equals(selectedCategory, ignoreCase = true) }
            }
            if (searchQuery.isNotBlank()) {
                val query = searchQuery.trim().lowercase()
                list = list.filter {
                    it.channel.name.lowercase().contains(query) ||
                            (it.currentProgramme?.title?.lowercase()?.contains(query) == true)
                }
            }
            return list
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

                // Match with existing EPG or prepare plain channels
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

                // If EPG URL provided or discovered in M3U header, auto-load EPG
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
            }.onFailure { err ->
                // Keep channels intact, but note EPG failure if needed
            }
        }
    }

    fun selectCategory(category: String) {
        _uiState.update { it.copy(selectedCategory = category) }
    }

    fun updateSearchQuery(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
    }

    fun getChannelSchedule(channel: M3uItem): List<EpgProgramme> {
        return _uiState.value.epgMatcher?.getSchedule(channel) ?: emptyList()
    }
}
