package com.iptv.app.core.parser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream

class M3uParserTest {

    private val parser = M3uParser()

    @Test
    fun testEmptyInput_returnsEmptyPlaylist() {
        val playlist = parser.parse("")
        assertTrue(playlist.items.isEmpty())
        assertTrue(playlist.groups.isEmpty())
    }

    @Test
    fun testHeaderDirectives_extractsUrlTvgAndAttributes() {
        val content = """
            #EXTM3U url-tvg="http://example.com/epg.xml.gz" tvg-shift="1"
            #EXTINF:-1 tvg-id="cnn" tvg-name="CNN HD" group-title="News",CNN News
            https://example.com/stream/cnn.m3u8
        """.trimIndent()

        val playlist = parser.parse(content)
        assertEquals("http://example.com/epg.xml.gz", playlist.epgUrl)
        assertEquals(1, playlist.items.size)
        assertEquals("1", playlist.headerAttributes["tvg-shift"])
    }

    @Test
    fun testAlternativeHeaderDirective_extractsXTvgUrl() {
        val content = """
            #EXTM3U x-tvg-url="https://epg.test/guide.xml"
            #EXTINF:-1,Sample TV
            https://example.com/stream/sample.m3u8
        """.trimIndent()

        val playlist = parser.parse(content)
        assertEquals("https://epg.test/guide.xml", playlist.epgUrl)
        assertEquals(1, playlist.items.size)
        assertEquals("Sample TV", playlist.items[0].name)
    }

    @Test
    fun testBasicChannelEntry_parsesTitleAndStreamUrl() {
        val content = """
            #EXTM3U
            #EXTINF:-1,BBC News 24
            http://stream.bbc.co.uk/live.m3u8
        """.trimIndent()

        val playlist = parser.parse(content)
        assertEquals(1, playlist.items.size)
        val item = playlist.items[0]
        assertEquals("BBC News 24", item.name)
        assertEquals("http://stream.bbc.co.uk/live.m3u8", item.streamUrl)
        assertEquals("General", item.group)
    }

    @Test
    fun testTvgAttributes_extractsIdNameLogoAndGroup() {
        val content = """
            #EXTM3U
            #EXTINF:-1 tvg-id="discovery.us" tvg-name="Discovery HD" tvg-logo="https://icons.com/disc.png" group-title="Documentaries" radio="false",Discovery Channel
            https://stream.discovery.com/hls/live.m3u8
        """.trimIndent()

        val playlist = parser.parse(content)
        assertEquals(1, playlist.items.size)
        val item = playlist.items[0]
        assertEquals("discovery.us", item.tvgId)
        assertEquals("Discovery HD", item.tvgName)
        assertEquals("https://icons.com/disc.png", item.logoUrl)
        assertEquals("Documentaries", item.group)
        assertEquals("Discovery Channel", item.name)
        assertEquals(false, item.isRadio)
        assertEquals(listOf("Documentaries"), playlist.groups)
    }

    @Test
    fun testCommasAndQuotesInAttributes() {
        val content = """
            #EXTM3U
            #EXTINF:-1 tvg-name="News, 24/7" group-title="News & Weather",News, 24/7 (Live, HD)
            https://stream.news.com/live.m3u8
        """.trimIndent()

        val playlist = parser.parse(content)
        assertEquals(1, playlist.items.size)
        val item = playlist.items[0]
        assertEquals("News, 24/7", item.tvgName)
        assertEquals("News & Weather", item.group)
        assertEquals("News, 24/7 (Live, HD)", item.name)
    }

    @Test
    fun testVlcOptions_extractsHeaders() {
        val content = """
            #EXTM3U
            #EXTINF:-1 tvg-id="sports1",Sky Sports
            #EXTVLCOPT:http-user-agent=CustomUserAgent/1.0
            #EXTVLCOPT:http-referrer=https://sports.com
            https://stream.sports.com/sky.m3u8
        """.trimIndent()

        val playlist = parser.parse(content)
        assertEquals(1, playlist.items.size)
        val item = playlist.items[0]
        assertEquals("CustomUserAgent/1.0", item.headers["User-Agent"])
        assertEquals("https://sports.com", item.headers["Referer"])
    }

    @Test
    fun testStreamingFromInputStream_handlesLargePlaylist() {
        val sb = StringBuilder()
        sb.append("#EXTM3U\n")
        val channelCount = 200
        for (i in 1..channelCount) {
            val group = if (i % 2 == 0) "Sports" else "Cinema"
            sb.append("""#EXTINF:-1 tvg-id="ch$i" group-title="$group",Channel $i""" + "\n")
            sb.append("https://stream.example.com/channel$i.m3u8\n")
        }

        val inputStream = ByteArrayInputStream(sb.toString().toByteArray())
        val playlist = parser.parse(inputStream)
        assertEquals(channelCount, playlist.items.size)
        assertEquals(listOf("Cinema", "Sports"), playlist.groups.sorted())
    }
}
