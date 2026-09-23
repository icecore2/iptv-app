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
}
