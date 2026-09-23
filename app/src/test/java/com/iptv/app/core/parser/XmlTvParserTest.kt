package com.iptv.app.core.parser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPOutputStream

class XmlTvParserTest {

    private val parser = XmlTvParser()

    @Test
    fun testParseXmlTv_channelsAndProgrammes() {
        val xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <tv generator-info-name="TestGenerator">
                <channel id="cnn">
                    <display-name>CNN International</display-name>
                    <icon src="https://images.com/cnn.png"/>
                </channel>
                <channel id="bbc1">
                    <display-name>BBC One</display-name>
                </channel>
                
                <programme start="20260922100000 +0000" stop="20260922110000 +0000" channel="cnn">
                    <title lang="en">Morning News</title>
                    <desc lang="en">Comprehensive world news updates.</desc>
                    <category>News</category>
                    <icon src="https://images.com/show1.png"/>
                </programme>
                <programme start="20260922110000 +0000" stop="20260922120000 +0000" channel="cnn">
                    <title lang="en">Connect the World</title>
                    <desc lang="en">Interviews with world leaders.</desc>
                    <category>News</category>
                </programme>
                <programme start="20260922100000 +0000" stop="20260922103000 +0000" channel="bbc1">
                    <title>Breakfast</title>
                </programme>
            </tv>
        """.trimIndent()

        val epgData = parser.parse(xml.byteInputStream())
        
        assertEquals(2, epgData.channels.size)
        val cnn = epgData.channels["cnn"]
        assertNotNull(cnn)
        assertEquals("CNN International", cnn!!.displayName)
        assertEquals("https://images.com/cnn.png", cnn.iconUrl)

        val bbc = epgData.channels["bbc1"]
        assertNotNull(bbc)
        assertEquals("BBC One", bbc!!.displayName)

        assertEquals(3, epgData.programmes.size)
        val p1 = epgData.programmes[0]
        assertEquals("cnn", p1.channelId)
        assertEquals("Morning News", p1.title)
        assertEquals("Comprehensive world news updates.", p1.description)
        assertEquals("News", p1.category)
        assertEquals("https://images.com/show1.png", p1.iconUrl)

        val p3 = epgData.programmes[2]
        assertEquals("bbc1", p3.channelId)
        assertEquals("Breakfast", p3.title)
    }

    @Test
    fun testParseXmlTv_gzipCompressedStream() {
        val xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <tv>
                <channel id="espn">
                    <display-name>ESPN HD</display-name>
                </channel>
                <programme start="20260922150000 +0000" stop="20260922170000 +0000" channel="espn">
                    <title>Live Football</title>
                    <category>Sports</category>
                </programme>
            </tv>
        """.trimIndent()

        // Compress XML with GZIP
        val byteOut = ByteArrayOutputStream()
        GZIPOutputStream(byteOut).use { gzip ->
            gzip.write(xml.toByteArray(Charsets.UTF_8))
        }
        val compressedBytes = byteOut.toByteArray()

        // Feed GZIP stream to parser
        val epgData = parser.parse(ByteArrayInputStream(compressedBytes), isGzip = true)
        assertEquals(1, epgData.channels.size)
        assertEquals("ESPN HD", epgData.channels["espn"]?.displayName)
        assertEquals(1, epgData.programmes.size)
        assertEquals("Live Football", epgData.programmes[0].title)
    }

    @Test
    fun testParseXmlTv_autoDetectGzipStream() {
        val xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <tv>
                <channel id="natgeo">
                    <display-name>National Geographic</display-name>
                </channel>
            </tv>
        """.trimIndent()

        val byteOut = ByteArrayOutputStream()
        GZIPOutputStream(byteOut).use { gzip ->
            gzip.write(xml.toByteArray(Charsets.UTF_8))
        }
        val compressedBytes = byteOut.toByteArray()

        // Pass without isGzip flag -> should auto-detect gzip magic bytes (0x1f, 0x8b)
        val epgData = parser.parse(ByteArrayInputStream(compressedBytes))
        assertEquals(1, epgData.channels.size)
        assertEquals("National Geographic", epgData.channels["natgeo"]?.displayName)
    }

    @Test
    fun testParseXmlTv_emptyOrInvalidInput_returnsEmptyData() {
        val emptyData = parser.parse(ByteArrayInputStream(ByteArray(0)))
        assertTrue(emptyData.channels.isEmpty())
        assertTrue(emptyData.programmes.isEmpty())
    }
}
