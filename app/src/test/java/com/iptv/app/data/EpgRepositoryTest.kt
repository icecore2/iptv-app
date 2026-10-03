package com.iptv.app.data

import com.iptv.app.core.parser.XmlTvParser
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.InputStream

class EpgRepositoryTest {

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
    fun testLoadEpgFromUrl_success() = runTest {
        val xmlContent = """
            <?xml version="1.0" encoding="UTF-8"?>
            <tv>
                <channel id="cnn">
                    <display-name>CNN International</display-name>
                </channel>
                <programme start="20260922120000 +0000" stop="20260922130000 +0000" channel="cnn">
                    <title>Live News</title>
                </programme>
            </tv>
        """.trimIndent()

        val fakeClient = FakeNetworkClient(ByteArrayInputStream(xmlContent.toByteArray()))
        val repository = EpgRepository(fakeClient, XmlTvParser())

        val result = repository.loadEpgFromUrl("http://sample.com/epg.xml")
        assertTrue(result.isSuccess)

        val epgData = result.getOrNull()
        assertNotNull(epgData)
        assertEquals(1, epgData!!.channels.size)
        assertEquals("CNN International", epgData.channels["cnn"]?.displayName)
        assertEquals(1, epgData.programmes.size)
        assertEquals("Live News", epgData.programmes[0].title)
    }

    @Test
    fun testLoadEpgFromUrl_failureReturnsErrorResult() = runTest {
        val fakeClient = FakeNetworkClient(null)
        val repository = EpgRepository(fakeClient, XmlTvParser())

        val result = repository.loadEpgFromUrl("http://invalid.url")
        assertTrue(result.isFailure)
    }
}
