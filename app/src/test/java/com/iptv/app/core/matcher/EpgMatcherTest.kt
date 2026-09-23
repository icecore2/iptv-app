package com.iptv.app.core.matcher

import com.iptv.app.core.model.EpgChannel
import com.iptv.app.core.model.EpgData
import com.iptv.app.core.model.EpgProgramme
import com.iptv.app.core.model.M3uItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EpgMatcherTest {

    private val epgData = EpgData(
        channels = mapOf(
            "cnn.us" to EpgChannel("cnn.us", "CNN International"),
            "bbc.one" to EpgChannel("bbc.one", "BBC One HD")
        ),
        programmes = listOf(
            EpgProgramme("cnn.us", "Morning News", 1000L, 2000L, description = "Early updates"),
            EpgProgramme("cnn.us", "Afternoon Live", 2000L, 3000L, description = "Midday news"),
            EpgProgramme("cnn.us", "Prime Time", 3000L, 4000L),
            EpgProgramme("bbc.one", "Breakfast Show", 1000L, 2500L)
        )
    )

    private val matcher = EpgMatcher(epgData)

    @Test
    fun testMatchExactTvgId() {
        val channel = M3uItem(id = "1", name = "Any Name", streamUrl = "http://url", tvgId = "cnn.us")
        val matchedEpgChannelId = matcher.findEpgChannelId(channel)
        assertEquals("cnn.us", matchedEpgChannelId)
    }

    @Test
    fun testMatchNormalizedName_fallback() {
        val channel = M3uItem(
            id = "2",
            name = "BBC One FHD [UK]",
            streamUrl = "http://url",
            tvgId = null
        )
        val matchedEpgChannelId = matcher.findEpgChannelId(channel)
        assertEquals("bbc.one", matchedEpgChannelId)
    }

    @Test
    fun testGetCurrentProgramme_atTimestamp() {
        val channel = M3uItem(id = "1", name = "CNN", streamUrl = "http://url", tvgId = "cnn.us")
        val current = matcher.getCurrentProgramme(channel, timestamp = 1500L)
        assertNotNull(current)
        assertEquals("Morning News", current!!.title)

        val afternoon = matcher.getCurrentProgramme(channel, timestamp = 2500L)
        assertNotNull(afternoon)
        assertEquals("Afternoon Live", afternoon!!.title)
    }

    @Test
    fun testGetNextProgramme_atTimestamp() {
        val channel = M3uItem(id = "1", name = "CNN", streamUrl = "http://url", tvgId = "cnn.us")
        val next = matcher.getNextProgramme(channel, timestamp = 1500L)
        assertNotNull(next)
        assertEquals("Afternoon Live", next!!.title)
    }

    @Test
    fun testCalculateProgress() {
        val programme = EpgProgramme("cnn.us", "Morning News", 1000L, 2000L)
        assertEquals(0.0f, programme.progress(1000L), 0.001f)
        assertEquals(0.5f, programme.progress(1500L), 0.001f)
        assertEquals(1.0f, programme.progress(2000L), 0.001f)
        assertEquals(1.0f, programme.progress(2500L), 0.001f)
        assertEquals(0.0f, programme.progress(500L), 0.001f)
    }

    @Test
    fun testEnrichChannelWithEpg() {
        val channel = M3uItem(id = "1", name = "CNN", streamUrl = "http://url", tvgId = "cnn.us")
        val enriched = matcher.enrichChannel(channel, timestamp = 1500L)

        assertEquals("CNN", enriched.channel.name)
        assertEquals("Morning News", enriched.currentProgramme?.title)
        assertEquals("Afternoon Live", enriched.nextProgramme?.title)
        assertEquals(0.5f, enriched.progress, 0.001f)
    }

    @Test
    fun testGetScheduleForChannel() {
        val channel = M3uItem(id = "1", name = "CNN", streamUrl = "http://url", tvgId = "cnn.us")
        val schedule = matcher.getSchedule(channel)
        assertEquals(3, schedule.size)
        assertEquals("Morning News", schedule[0].title)
        assertEquals("Prime Time", schedule[2].title)
    }
}
