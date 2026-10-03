package com.iptv.app.data

import com.iptv.app.core.parser.M3uParser
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.InputStream

class PlaylistRepositoryTest {

    private class FakeNetworkClient(private val responseStream: InputStream?) : NetworkClient {
        override suspend fun openStream(url: String, headers: Map<String, String>): InputStream {
            return responseStream ?: throw IllegalArgumentException("Network error for $url")
        }
        override suspend fun postForm(url: String, formParams: Map<String, String>, headers: Map<String, String>): InputStream {
            return responseStream ?: throw IllegalArgumentException("Network error for $url")
        }
        override suspend fun postJson(url: String, jsonBody: String, headers: Map<String, String>): InputStream {
            return responseStream ?: throw IllegalArgumentException("Network error for $url")
        }
    }

    @Test
    fun testLoadPlaylistFromUrl_success() = runTest {
        val m3uContent = """
            #EXTM3U url-tvg="http://sample.com/epg.xml.gz"
            #EXTINF:-1 tvg-id="cnn" group-title="News",CNN
            http://stream.com/cnn.m3u8
            #EXTINF:-1 tvg-id="espn" group-title="Sports",ESPN
            http://stream.com/espn.m3u8
        """.trimIndent()

        val fakeClient = FakeNetworkClient(ByteArrayInputStream(m3uContent.toByteArray()))
        val repository = PlaylistRepository(fakeClient, M3uParser())

        val result = repository.loadPlaylistFromUrl("http://sample.com/playlist.m3u")
        assertTrue(result.isSuccess)

        val playlist = result.getOrNull()
        assertNotNull(playlist)
        assertEquals(2, playlist!!.items.size)
        assertEquals(listOf("News", "Sports"), playlist.groups)
        assertEquals("http://sample.com/epg.xml.gz", playlist.epgUrl)
    }

    @Test
    fun testLoadPlaylistFromUrl_failureReturnsErrorResult() = runTest {
        val fakeClient = FakeNetworkClient(null)
        val repository = PlaylistRepository(fakeClient, M3uParser())

        val result = repository.loadPlaylistFromUrl("http://invalid.url")
        assertTrue(result.isFailure)
    }

    @Test
    fun testLoadPlaylistFromString_returnsParsed() {
        val repository = PlaylistRepository(FakeNetworkClient(null), M3uParser())
        val playlist = repository.loadPlaylistFromString(
            """
            #EXTM3U
            #EXTINF:-1,Test Channel
            http://stream/live.m3u8
            """.trimIndent()
        )
        assertEquals(1, playlist.items.size)
        assertEquals("Test Channel", playlist.items[0].name)
    }
}
