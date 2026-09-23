package com.iptv.app.ui.viewmodel

import com.iptv.app.core.model.M3uItem
import com.iptv.app.core.model.M3uPlaylist
import com.iptv.app.core.parser.M3uParser
import com.iptv.app.core.parser.XmlTvParser
import com.iptv.app.data.EpgRepository
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
}
