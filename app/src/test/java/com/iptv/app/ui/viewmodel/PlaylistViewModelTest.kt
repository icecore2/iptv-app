package com.iptv.app.ui.viewmodel

import com.iptv.app.core.model.M3uItem
import com.iptv.app.core.model.M3uPlaylist
import com.iptv.app.core.parser.M3uParser
import com.iptv.app.core.parser.XmlTvParser
import com.iptv.app.core.model.SavedPlaylistPair
import com.iptv.app.data.DEFAULT_SAMPLE_PAIR
import com.iptv.app.data.EpgRepository
import com.iptv.app.data.InMemorySavedPlaylistRepository
import com.iptv.app.data.NetworkClient
import com.iptv.app.data.PlaylistRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.InputStream

@OptIn(ExperimentalCoroutinesApi::class)
class PlaylistViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private class FakeNetworkClient(
        private val playlistContent: String? = null,
        private val epgContent: String? = null
    ) : NetworkClient {
        override suspend fun openStream(url: String, headers: Map<String, String>): InputStream {
            if (url.contains("epg") && epgContent != null) {
                return ByteArrayInputStream(epgContent.toByteArray())
            }
            if (playlistContent != null) {
                return ByteArrayInputStream(playlistContent.toByteArray())
            }
            throw IllegalArgumentException("Fake network error for $url")
        }

        override suspend fun postForm(url: String, formParams: Map<String, String>, headers: Map<String, String>): InputStream {
            throw IllegalArgumentException("Fake network error for $url")
        }

        override suspend fun postJson(url: String, jsonBody: String, headers: Map<String, String>): InputStream {
            throw IllegalArgumentException("Fake network error for $url")
        }
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun testLoadSampleData_populatesChannelsAndCategories() {
        val fakeClient = FakeNetworkClient()
        val playlistRepo = PlaylistRepository(fakeClient, ioDispatcher = testDispatcher)
        val epgRepo = EpgRepository(fakeClient, ioDispatcher = testDispatcher)
        val viewModel = PlaylistViewModel(playlistRepo, epgRepo)

        viewModel.loadSampleData()

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertTrue(state.channels.isNotEmpty())
        assertTrue(state.categories.contains("All"))
        assertTrue(state.categories.contains("News"))
        assertTrue(state.categories.contains("Sports"))
        assertNotNull(state.epgData)
    }

    @Test
    fun testFilterByCategory() {
        val fakeClient = FakeNetworkClient()
        val viewModel = PlaylistViewModel(
            PlaylistRepository(fakeClient, ioDispatcher = testDispatcher),
            EpgRepository(fakeClient, ioDispatcher = testDispatcher)
        )
        viewModel.loadSampleData()

        viewModel.selectCategory("News")
        val newsChannels = viewModel.uiState.value.filteredChannels
        assertTrue(newsChannels.isNotEmpty())
        assertTrue(newsChannels.all { it.channel.group == "News" })
    }

    @Test
    fun testSearchQueryFiltering() {
        val fakeClient = FakeNetworkClient()
        val viewModel = PlaylistViewModel(
            PlaylistRepository(fakeClient, ioDispatcher = testDispatcher),
            EpgRepository(fakeClient, ioDispatcher = testDispatcher)
        )
        viewModel.loadSampleData()

        viewModel.updateSearchQuery("NASA")
        val filtered = viewModel.uiState.value.filteredChannels
        assertEquals(1, filtered.size)
        assertEquals("NASA TV Public", filtered[0].channel.name)
    }

    @Test
    fun testLoadCustomPlaylistUrl_success() = runTest {
        val m3u = """
            #EXTM3U
            #EXTINF:-1 group-title="Cinema",Movie Channel
            http://stream/movie.m3u8
        """.trimIndent()

        val fakeClient = FakeNetworkClient(playlistContent = m3u)
        val playlistRepo = PlaylistRepository(fakeClient, ioDispatcher = testDispatcher)
        val epgRepo = EpgRepository(fakeClient, ioDispatcher = testDispatcher)
        val viewModel = PlaylistViewModel(playlistRepo, epgRepo)

        viewModel.loadPlaylist("http://remote.url/list.m3u")
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertEquals(1, state.channels.size)
        assertEquals("Movie Channel", state.channels[0].channel.name)
    }

    @Test
    fun testFilterOnlyWithEpgAndLiveNow() {
        val fakeClient = FakeNetworkClient()
        val viewModel = PlaylistViewModel(
            PlaylistRepository(fakeClient, ioDispatcher = testDispatcher),
            EpgRepository(fakeClient, ioDispatcher = testDispatcher)
        )
        viewModel.loadSampleData()

        viewModel.setFilterOptions(onlyWithEpg = true)
        val withEpg = viewModel.uiState.value.filteredChannels
        assertTrue(withEpg.all { it.currentProgramme != null || it.nextProgramme != null })

        viewModel.setFilterOptions(onlyLiveNow = true)
        val liveNow = viewModel.uiState.value.filteredChannels
        assertTrue(liveNow.all { it.currentProgramme != null })
    }

    @Test
    fun testSortChannelsByName() {
        val fakeClient = FakeNetworkClient()
        val viewModel = PlaylistViewModel(
            PlaylistRepository(fakeClient, ioDispatcher = testDispatcher),
            EpgRepository(fakeClient, ioDispatcher = testDispatcher)
        )
        viewModel.loadSampleData()

        viewModel.setFilterOptions(sortBy = ChannelSortOrder.NAME_ASC)
        val sortedAsc = viewModel.uiState.value.filteredChannels.map { it.channel.name }
        assertEquals(sortedAsc.sorted(), sortedAsc)

        viewModel.setFilterOptions(sortBy = ChannelSortOrder.NAME_DESC)
        val sortedDesc = viewModel.uiState.value.filteredChannels.map { it.channel.name }
        assertEquals(sortedDesc.sortedDescending(), sortedDesc)
    }

    @Test
    fun testResetFilters() {
        val fakeClient = FakeNetworkClient()
        val viewModel = PlaylistViewModel(
            PlaylistRepository(fakeClient, ioDispatcher = testDispatcher),
            EpgRepository(fakeClient, ioDispatcher = testDispatcher)
        )
        viewModel.loadSampleData()

        viewModel.setFilterOptions(category = "News", onlyWithEpg = true, sortBy = ChannelSortOrder.NAME_ASC)
        assertTrue(viewModel.hasActiveFilters())

        viewModel.resetFilters()
        assertFalse(viewModel.hasActiveFilters())
        assertEquals("All", viewModel.uiState.value.selectedCategory)
        assertFalse(viewModel.uiState.value.onlyWithEpg)
        assertEquals(ChannelSortOrder.DEFAULT, viewModel.uiState.value.sortBy)
    }

    @Test
    fun testFavorites_toggleAndFilter() {
        val fakeClient = FakeNetworkClient()
        val viewModel = PlaylistViewModel(
            PlaylistRepository(fakeClient, ioDispatcher = testDispatcher),
            EpgRepository(fakeClient, ioDispatcher = testDispatcher)
        )
        viewModel.loadSampleData()

        val channel = viewModel.uiState.value.channels.first().channel
        assertFalse(viewModel.isFavorite(channel.id))

        // Toggle to add
        viewModel.toggleFavorite(channel.id)
        assertTrue(viewModel.isFavorite(channel.id))

        // Filter by Favorites
        viewModel.setContentType(ContentTypeFilter.FAVORITES)
        val favorites = viewModel.uiState.value.filteredChannels
        assertEquals(1, favorites.size)
        assertEquals(channel.id, favorites[0].channel.id)

        // Toggle to remove
        viewModel.toggleFavorite(channel.id)
        assertFalse(viewModel.isFavorite(channel.id))
        assertTrue(viewModel.uiState.value.filteredChannels.isEmpty())
    }

    @Test
    fun testContentTypeFilter_LiveVsVod() {
        val fakeClient = FakeNetworkClient()
        val viewModel = PlaylistViewModel(
            PlaylistRepository(fakeClient, ioDispatcher = testDispatcher),
            EpgRepository(fakeClient, ioDispatcher = testDispatcher)
        )
        viewModel.loadSampleData()

        viewModel.setContentType(ContentTypeFilter.VOD)
        val vodList = viewModel.uiState.value.filteredChannels
        assertTrue(vodList.isNotEmpty())
        assertTrue(vodList.all { it.channel.isVod })

        viewModel.setContentType(ContentTypeFilter.LIVE_TV)
        val liveList = viewModel.uiState.value.filteredChannels
        assertTrue(liveList.isNotEmpty())
        assertTrue(liveList.all { !it.channel.isVod })
    }

    @Test
    fun testRecentChannels() {
        val fakeClient = FakeNetworkClient()
        val viewModel = PlaylistViewModel(
            PlaylistRepository(fakeClient, ioDispatcher = testDispatcher),
            EpgRepository(fakeClient, ioDispatcher = testDispatcher)
        )
        viewModel.loadSampleData()

        val ch1 = viewModel.uiState.value.channels[0].channel
        val ch2 = viewModel.uiState.value.channels[1].channel

        viewModel.addRecentChannel(ch1)
        viewModel.addRecentChannel(ch2)

        val recents = viewModel.uiState.value.recentChannels
        assertEquals(2, recents.size)
        assertEquals(ch2.id, recents[0].id) // Most recent first
        assertEquals(ch1.id, recents[1].id)
    }

    @Test
    fun testViewModeToggle() {
        val fakeClient = FakeNetworkClient()
        val viewModel = PlaylistViewModel(
            PlaylistRepository(fakeClient, ioDispatcher = testDispatcher),
            EpgRepository(fakeClient, ioDispatcher = testDispatcher)
        )
        assertEquals(ViewMode.LIST, viewModel.uiState.value.viewMode)

        viewModel.toggleViewMode()
        assertEquals(ViewMode.GRID, viewModel.uiState.value.viewMode)

        viewModel.toggleViewMode()
        assertEquals(ViewMode.LIST, viewModel.uiState.value.viewMode)
    }

    @Test
    fun testClearPlaylist_resetsState() {
        val fakeClient = FakeNetworkClient()
        val viewModel = PlaylistViewModel(
            PlaylistRepository(fakeClient, ioDispatcher = testDispatcher),
            EpgRepository(fakeClient, ioDispatcher = testDispatcher)
        )
        viewModel.loadSampleData()

        // Verify channels are loaded
        assertTrue(viewModel.uiState.value.channels.isNotEmpty())
        assertNotNull(viewModel.uiState.value.epgData)
        assertNotNull(viewModel.uiState.value.epgMatcher)

        // Set some filters to verify they get reset too
        viewModel.selectCategory("News")
        viewModel.setFilterOptions(onlyWithEpg = true, sortBy = ChannelSortOrder.NAME_ASC)
        viewModel.setContentType(ContentTypeFilter.LIVE_TV)
        viewModel.updateSearchQuery("test")

        // Clear the playlist
        viewModel.clearPlaylist()

        val state = viewModel.uiState.value
        assertTrue(state.channels.isEmpty())
        assertFalse(state.isLoading)
        assertEquals(null, state.error)
        assertEquals(null, state.playlist)
        assertEquals(null, state.currentUrl)
        assertEquals(null, state.currentEpgUrl)
        assertEquals(null, state.epgData)
        assertEquals(null, state.epgMatcher)
        assertEquals(listOf("All"), state.categories)
        assertEquals("All", state.selectedCategory)
        assertEquals("", state.searchQuery)
        assertFalse(state.onlyWithEpg)
        assertFalse(state.onlyLiveNow)
        assertEquals(ChannelSortOrder.DEFAULT, state.sortBy)
        assertEquals(ContentTypeFilter.ALL, state.contentType)
    }

    @Test
    fun testClearPlaylist_preservesFavoritesAndRecents() {
        val fakeClient = FakeNetworkClient()
        val viewModel = PlaylistViewModel(
            PlaylistRepository(fakeClient, ioDispatcher = testDispatcher),
            EpgRepository(fakeClient, ioDispatcher = testDispatcher)
        )
        viewModel.loadSampleData()

        // Add a favorite and a recent channel
        val ch1 = viewModel.uiState.value.channels[0].channel
        val ch2 = viewModel.uiState.value.channels[1].channel
        viewModel.toggleFavorite(ch1.id)
        viewModel.addRecentChannel(ch2)

        assertTrue(viewModel.uiState.value.favoriteIds.contains(ch1.id))
        assertEquals(1, viewModel.uiState.value.recentChannels.size)

        // Clear the playlist
        viewModel.clearPlaylist()

        // Favorites and recents should be preserved
        val state = viewModel.uiState.value
        assertTrue(state.favoriteIds.contains(ch1.id))
        assertEquals(1, state.recentChannels.size)
        assertEquals(ch2.id, state.recentChannels[0].id)

        // But channels and EPG should be cleared
        assertTrue(state.channels.isEmpty())
        assertEquals(null, state.epgData)
    }

    @Test
    fun testLoadSavedPlaylists_hasDefaultDemoPair() = runTest {
        val fakeClient = FakeNetworkClient()
        val savedRepo = InMemorySavedPlaylistRepository()
        val viewModel = PlaylistViewModel(
            PlaylistRepository(fakeClient, ioDispatcher = testDispatcher),
            EpgRepository(fakeClient, ioDispatcher = testDispatcher),
            savedRepo
        )
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(1, state.savedPlaylists.size)
        assertEquals("Demo Channels & EPG", state.savedPlaylists.first().name)
        assertTrue(state.savedPlaylists.first().isSample)
        assertEquals(DEFAULT_SAMPLE_PAIR.id, state.activePairId)
    }

    @Test
    fun testSaveAndSwitchPlaylist_loadsChannelsAndUpdatesActive() = runTest {
        val m3u = """
            #EXTM3U
            #EXTINF:-1 group-title="News",News 24
            http://stream/news24.m3u8
        """.trimIndent()

        val fakeClient = FakeNetworkClient(playlistContent = m3u)
        val savedRepo = InMemorySavedPlaylistRepository()
        val viewModel = PlaylistViewModel(
            PlaylistRepository(fakeClient, ioDispatcher = testDispatcher),
            EpgRepository(fakeClient, ioDispatcher = testDispatcher),
            savedRepo
        )
        advanceUntilIdle()

        viewModel.saveAndSwitch(
            name = "My Custom News",
            playlistUrl = "http://stream/news.m3u",
            epgUrl = null
        )
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(2, state.savedPlaylists.size)
        assertEquals("My Custom News", state.activePair?.name)
        assertEquals(1, state.channels.size)
        assertEquals("News 24", state.channels[0].channel.name)
    }

    @Test
    fun testSwitchToSavedPlaylist_switchesBetweenPairs() = runTest {
        val m3u = """
            #EXTM3U
            #EXTINF:-1 group-title="Sports",Sports HD
            http://stream/sports.m3u8
        """.trimIndent()

        val fakeClient = FakeNetworkClient(playlistContent = m3u)
        val customPair = SavedPlaylistPair(
            id = "custom_sports",
            name = "Sports TV",
            playlistUrl = "http://sports/list.m3u"
        )
        val savedRepo = InMemorySavedPlaylistRepository(
            initialList = listOf(DEFAULT_SAMPLE_PAIR, customPair)
        )
        val viewModel = PlaylistViewModel(
            PlaylistRepository(fakeClient, ioDispatcher = testDispatcher),
            EpgRepository(fakeClient, ioDispatcher = testDispatcher),
            savedRepo
        )
        advanceUntilIdle()

        // Initially switch to custom
        viewModel.switchToPlaylist(customPair)
        advanceUntilIdle()

        assertEquals("custom_sports", viewModel.uiState.value.activePairId)
        assertEquals(1, viewModel.uiState.value.channels.size)
        assertEquals("Sports HD", viewModel.uiState.value.channels[0].channel.name)

        // Switch back to demo sample
        viewModel.switchToPlaylist(DEFAULT_SAMPLE_PAIR)
        advanceUntilIdle()

        assertEquals(DEFAULT_SAMPLE_PAIR.id, viewModel.uiState.value.activePairId)
        assertTrue(viewModel.uiState.value.channels.size > 1)
    }

    @Test
    fun testUpdateSavedPlaylist() = runTest {
        val fakeClient = FakeNetworkClient()
        val customPair = SavedPlaylistPair(
            id = "custom_1",
            name = "Original Name",
            playlistUrl = "http://test/list.m3u"
        )
        val savedRepo = InMemorySavedPlaylistRepository(
            initialList = listOf(DEFAULT_SAMPLE_PAIR, customPair)
        )
        val viewModel = PlaylistViewModel(
            PlaylistRepository(fakeClient, ioDispatcher = testDispatcher),
            EpgRepository(fakeClient, ioDispatcher = testDispatcher),
            savedRepo
        )
        advanceUntilIdle()

        viewModel.updatePlaylist(customPair.copy(name = "Renamed Playlist"))
        advanceUntilIdle()

        val updated = viewModel.uiState.value.savedPlaylists.find { it.id == "custom_1" }
        assertEquals("Renamed Playlist", updated?.name)
    }

    @Test
    fun testDeleteSavedPlaylist_fallsBackToNextOrClears() = runTest {
        val fakeClient = FakeNetworkClient()
        val customPair = SavedPlaylistPair(
            id = "custom_del",
            name = "Delete Me",
            playlistUrl = "http://test/list.m3u"
        )
        val savedRepo = InMemorySavedPlaylistRepository(
            initialList = listOf(DEFAULT_SAMPLE_PAIR, customPair)
        )
        val viewModel = PlaylistViewModel(
            PlaylistRepository(fakeClient, ioDispatcher = testDispatcher),
            EpgRepository(fakeClient, ioDispatcher = testDispatcher),
            savedRepo
        )
        advanceUntilIdle()

        // Set as active
        viewModel.switchToPlaylist(customPair)
        advanceUntilIdle()
        assertEquals("custom_del", viewModel.uiState.value.activePairId)

        // Delete active pair
        viewModel.deletePlaylist("custom_del")
        advanceUntilIdle()

        // Should fall back to remaining pair (demo)
        assertEquals(1, viewModel.uiState.value.savedPlaylists.size)
        assertEquals(DEFAULT_SAMPLE_PAIR.id, viewModel.uiState.value.activePairId)
    }

    @Test
    fun testPlaylistScopedFavorites_restoresFavoritesOnSwitch() = runTest {
        val fakeClient = FakeNetworkClient()
        val pair1 = SavedPlaylistPair(
            id = "p1",
            name = "Pair 1",
            playlistUrl = "http://p1/list.m3u",
            favoriteIds = setOf("p1_ch1")
        )
        val pair2 = SavedPlaylistPair(
            id = "p2",
            name = "Pair 2",
            playlistUrl = "http://p2/list.m3u",
            favoriteIds = setOf("p2_chA", "p2_chB")
        )
        val savedRepo = InMemorySavedPlaylistRepository(listOf(pair1, pair2))
        val viewModel = PlaylistViewModel(
            PlaylistRepository(fakeClient, ioDispatcher = testDispatcher),
            EpgRepository(fakeClient, ioDispatcher = testDispatcher),
            savedRepo
        )
        advanceUntilIdle()

        // Switch to pair1
        viewModel.switchToPlaylist(pair1)
        advanceUntilIdle()
        assertEquals(setOf("p1_ch1"), viewModel.uiState.value.favoriteIds)

        // Switch to pair2
        viewModel.switchToPlaylist(pair2)
        advanceUntilIdle()
        assertEquals(setOf("p2_chA", "p2_chB"), viewModel.uiState.value.favoriteIds)
    }
}
