package com.iptv.app.core.matcher

import com.iptv.app.core.model.EpgProgramme
import com.iptv.app.core.model.M3uItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CatchupResolverTest {

    private val sampleProgramme = EpgProgramme(
        channelId = "ch1",
        title = "Morning News",
        startEpochMillis = 1700000000000L, // 1700000000 sec
        stopEpochMillis = 1700003600000L,  // 1700003600 sec (1 hour duration)
        description = "Daily morning news report"
    )

    @Test
    fun testBuildVodUrl_whenChannelIsDirectVod_returnsOriginalStreamUrl() {
        val vodChannel = M3uItem(
            id = "movie1",
            name = "Test Movie",
            streamUrl = "https://cdn.example.com/movies/sample.mp4",
            group = "Movies"
        )

        val url = CatchupResolver.buildVodUrl(vodChannel, sampleProgramme)
        assertEquals("https://cdn.example.com/movies/sample.mp4", url)
    }

    @Test
    fun testBuildVodUrl_whenCatchupSourceTemplateSpecified_replacesPlaceholders() {
        val channel = M3uItem(
            id = "ch1",
            name = "Channel 1",
            streamUrl = "http://iptv.server.com/live/ch1.m3u8",
            tvgId = "tvg_ch1",
            catchup = "append",
            catchupSource = "http://iptv.server.com/archive/\${catchup-id}/\${start}/\${duration}.m3u8"
        )

        val url = CatchupResolver.buildVodUrl(channel, sampleProgramme)
        val expected = "http://iptv.server.com/archive/tvg_ch1/1700000000/3600.m3u8"
        assertEquals(expected, url)
    }

    @Test
    fun testBuildVodUrl_whenCatchupSourceIsQueryTemplate_appendsToStreamUrl() {
        val channel = M3uItem(
            id = "ch1",
            name = "Channel 1",
            streamUrl = "http://iptv.server.com/live/ch1.m3u8",
            catchup = "append",
            catchupSource = "?utc=\${start}&lutc=\${end}"
        )

        val url = CatchupResolver.buildVodUrl(channel, sampleProgramme)
        val expected = "http://iptv.server.com/live/ch1.m3u8?utc=1700000000&lutc=1700003600"
        assertEquals(expected, url)
    }

    @Test
    fun testBuildVodUrl_whenFlussonicCatchup_formatsTimeshiftAbsUrl() {
        val channel = M3uItem(
            id = "ch1",
            name = "Channel 1",
            streamUrl = "http://flussonic.server.com/live/stream.m3u8",
            catchup = "flussonic"
        )

        val url = CatchupResolver.buildVodUrl(channel, sampleProgramme)
        val expected = "http://flussonic.server.com/live/timeshift_abs-1700000000.m3u8"
        assertEquals(expected, url)
    }

    @Test
    fun testBuildVodUrl_whenXtreamCodesCatchup_formatsTimeshiftUrl() {
        val channel = M3uItem(
            id = "ch1",
            name = "Channel 1",
            streamUrl = "http://xc.server.com:8080/live/user1/pass1/12345.ts",
            catchup = "xc"
        )

        val url = CatchupResolver.buildVodUrl(channel, sampleProgramme)
        // startEpoch 1700000000000 in UTC is 2023-11-14:22-13
        assertTrue(url.contains("/timeshift/user1/pass1/60/"))
        assertTrue(url.endsWith("/12345.ts"))
    }

    @Test
    fun testBuildVodUrl_whenDefaultAppendFallback_appendsUtcParams() {
        val channel = M3uItem(
            id = "ch1",
            name = "Channel 1",
            streamUrl = "http://stream.server.com/live.m3u8"
        )

        val url = CatchupResolver.buildVodUrl(channel, sampleProgramme)
        assertEquals("http://stream.server.com/live.m3u8?utc=1700000000&lutc=1700003600", url)
    }

    @Test
    fun testHasCatchupSupport() {
        val chWithoutCatchup = M3uItem("1", "Ch1", "http://live.m3u8")
        // Even without explicit catchup tag, if isVod is true or catchup tags exist
        val chWithCatchup = M3uItem("2", "Ch2", "http://live.m3u8", catchup = "append")
        val chVod = M3uItem("3", "Ch3", "http://movie.mp4")

        assertTrue(CatchupResolver.hasCatchupSupport(chWithCatchup))
        assertTrue(CatchupResolver.hasCatchupSupport(chVod))
    }
}
