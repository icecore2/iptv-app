package com.iptv.app.data

import com.iptv.app.core.model.SavedPlaylistPair
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SavedPlaylistRepositoryTest {

    @Test
    fun testDefaultInitialization_hasSamplePair() = runTest {
        val repo = InMemorySavedPlaylistRepository()
        val list = repo.getSavedPlaylists()

        assertEquals(1, list.size)
        val defaultPair = list.first()
        assertEquals("Demo Channels & EPG", defaultPair.name)
        assertTrue(defaultPair.isSample)
        assertEquals(defaultPair.id, repo.getActivePairId())
    }

    @Test
    fun testSaveAndRetrievePlaylist() = runTest {
        val repo = InMemorySavedPlaylistRepository()
        val newPair = SavedPlaylistPair(
            name = "My Cable IPTV",
            playlistUrl = "http://example.com/playlist.m3u",
            epgUrl = "http://example.com/epg.xml"
        )

        repo.savePlaylist(newPair)
        val list = repo.getSavedPlaylists()

        assertEquals(2, list.size)
        val saved = list.find { it.id == newPair.id }
        assertNotNull(saved)
        assertEquals("My Cable IPTV", saved?.name)
        assertEquals("http://example.com/playlist.m3u", saved?.playlistUrl)
        assertEquals("http://example.com/epg.xml", saved?.epgUrl)
    }

    @Test
    fun testUpdatePlaylist() = runTest {
        val repo = InMemorySavedPlaylistRepository()
        val newPair = SavedPlaylistPair(
            name = "Original Name",
            playlistUrl = "http://example.com/playlist.m3u"
        )
        repo.savePlaylist(newPair)

        val updated = newPair.copy(name = "Updated Name", epgUrl = "http://example.com/epg.xml")
        repo.updatePlaylist(updated)

        val list = repo.getSavedPlaylists()
        val found = list.find { it.id == newPair.id }
        assertEquals("Updated Name", found?.name)
        assertEquals("http://example.com/epg.xml", found?.epgUrl)
    }

    @Test
    fun testDeletePlaylist_andClearsActiveIdIfActive() = runTest {
        val repo = InMemorySavedPlaylistRepository()
        val pair = SavedPlaylistPair(
            name = "To Delete",
            playlistUrl = "http://delete.me/list.m3u"
        )
        repo.savePlaylist(pair)
        repo.setActivePairId(pair.id)
        assertEquals(pair.id, repo.getActivePairId())

        repo.deletePlaylist(pair.id)
        val list = repo.getSavedPlaylists()
        assertFalse(list.any { it.id == pair.id })
        assertNull(repo.getActivePairId())
    }

    @Test
    fun testUpdateFavoritesPerPlaylist() = runTest {
        val repo = InMemorySavedPlaylistRepository()
        val pair = SavedPlaylistPair(
            name = "Sports",
            playlistUrl = "http://sports.com/list.m3u"
        )
        repo.savePlaylist(pair)

        repo.updateFavorites(pair.id, setOf("ch_1", "ch_2"))
        val list = repo.getSavedPlaylists()
        val saved = list.find { it.id == pair.id }

        assertEquals(setOf("ch_1", "ch_2"), saved?.favoriteIds)
    }
}
