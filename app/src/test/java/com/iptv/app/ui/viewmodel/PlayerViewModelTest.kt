package com.iptv.app.ui.viewmodel

import com.iptv.app.core.matcher.EpgMatcher
import com.iptv.app.core.model.EpgChannel
import com.iptv.app.core.model.EpgData
import com.iptv.app.core.model.EpgProgramme
import com.iptv.app.core.model.M3uItem
import android.graphics.Bitmap
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerViewModelTest {

    private val channels = listOf(
        M3uItem("1", "Channel 1", "http://stream1.m3u8", tvgId = "ch1"),
        M3uItem("2", "Channel 2", "http://stream2.m3u8", tvgId = "ch2"),
        M3uItem("3", "Channel 3", "http://stream3.m3u8", tvgId = "ch3")
    )

    private val epgData = EpgData(
        channels = mapOf(
            "ch1" to EpgChannel("ch1", "Channel 1"),
            "ch2" to EpgChannel("ch2", "Channel 2")
        ),
        programmes = listOf(
            EpgProgramme("ch1", "Show 1", 1000L, 2000L)
        )
    )

    private val matcher = EpgMatcher(epgData)

    @Test
    fun testPlayChannel_setsCurrentChannelAndEpg() {
        val vm = PlayerViewModel()
        vm.playChannel(channels[0], channels, matcher)

        val state = vm.uiState.value
        assertEquals("Channel 1", state.currentChannel?.name)
        assertNotNull(state.currentEpg)
        assertEquals(3, state.channelList.size)
    }

    @Test
    fun testPlayNextChannel_cyclesForward() {
        val vm = PlayerViewModel()
        vm.playChannel(channels[0], channels, matcher)

        vm.playNext()
        assertEquals("Channel 2", vm.uiState.value.currentChannel?.name)

        vm.playNext()
        assertEquals("Channel 3", vm.uiState.value.currentChannel?.name)

        // Wraps around
        vm.playNext()
        assertEquals("Channel 1", vm.uiState.value.currentChannel?.name)
    }

    @Test
    fun testPlayPreviousChannel_cyclesBackward() {
        val vm = PlayerViewModel()
        vm.playChannel(channels[0], channels, matcher)

        // Previous from first wraps around to last
        vm.playPrevious()
        assertEquals("Channel 3", vm.uiState.value.currentChannel?.name)

        vm.playPrevious()
        assertEquals("Channel 2", vm.uiState.value.currentChannel?.name)
    }

    @Test
    fun testCycleAspectRatio() {
        val vm = PlayerViewModel()
        assertEquals(AspectRatioMode.FIT, vm.uiState.value.aspectRatioMode)

        vm.cycleAspectRatio()
        assertEquals(AspectRatioMode.ZOOM, vm.uiState.value.aspectRatioMode)

        vm.cycleAspectRatio()
        assertEquals(AspectRatioMode.FILL, vm.uiState.value.aspectRatioMode)

        vm.cycleAspectRatio()
        assertEquals(AspectRatioMode.FIT, vm.uiState.value.aspectRatioMode)
    }

    @Test
    fun testSetPlaybackStates() {
        val vm = PlayerViewModel()
        vm.setBuffering(true)
        assertTrue(vm.uiState.value.isBuffering)

        vm.setPlaying(true)
        vm.setBuffering(false)
        assertTrue(vm.uiState.value.isPlaying)
        assertFalse(vm.uiState.value.isBuffering)

        vm.setError("Stream connection failed")
        assertEquals("Stream connection failed", vm.uiState.value.errorMessage)
    }

    @Test
    fun testStreamInfo_update() {
        val vm = PlayerViewModel()
        val info = StreamInfo(
            resolution = "1920x1080 (Full HD)",
            bitrate = "4.5 Mbps",
            videoCodec = "H.264 / AVC",
            audioCodec = "AAC-LC",
            frameRate = "60 fps",
            streamFormat = "HLS (.m3u8)",
            streamUrl = "https://stream.com/live.m3u8",
            bufferPercentage = 85
        )
        vm.updateStreamInfo(info)

        assertEquals("1920x1080 (Full HD)", vm.uiState.value.streamInfo.resolution)
        assertEquals("4.5 Mbps", vm.uiState.value.streamInfo.bitrate)
        assertEquals(85, vm.uiState.value.streamInfo.bufferPercentage)
    }

    @Test
    fun testToggleLock() {
        val vm = PlayerViewModel()
        assertFalse(vm.uiState.value.isLocked)

        vm.toggleLock()
        assertTrue(vm.uiState.value.isLocked)

        vm.toggleLock()
        assertFalse(vm.uiState.value.isLocked)
    }

    @Test
    fun testToggleMute() {
        val vm = PlayerViewModel()
        assertFalse(vm.uiState.value.isMuted)

        vm.toggleMute()
        assertTrue(vm.uiState.value.isMuted)

        vm.toggleMute()
        assertFalse(vm.uiState.value.isMuted)
    }

    @Test
    fun testPlaybackSpeed() {
        val vm = PlayerViewModel()
        assertEquals(1.0f, vm.uiState.value.playbackSpeed, 0.01f)

        vm.setPlaybackSpeed(1.5f)
        assertEquals(1.5f, vm.uiState.value.playbackSpeed, 0.01f)
    }

    @Test
    fun testSleepTimer() {
        val vm = PlayerViewModel()
        assertEquals(null, vm.uiState.value.sleepTimerMinutesRemaining)

        vm.setSleepTimer(30)
        assertEquals(30, vm.uiState.value.sleepTimerMinutesRemaining)

        vm.cancelSleepTimer()
        assertEquals(null, vm.uiState.value.sleepTimerMinutesRemaining)
    }

    @Test
    fun testDialogVisibilities() {
        val vm = PlayerViewModel()
        assertFalse(vm.uiState.value.isStreamInfoDialogVisible)
        assertFalse(vm.uiState.value.isEpgSheetVisible)
        assertFalse(vm.uiState.value.isChannelSelectorVisible)

        vm.setStreamInfoDialogVisible(true)
        assertTrue(vm.uiState.value.isStreamInfoDialogVisible)

        vm.setEpgSheetVisible(true)
        assertTrue(vm.uiState.value.isEpgSheetVisible)

        vm.setChannelSelectorVisible(true)
        assertTrue(vm.uiState.value.isChannelSelectorVisible)
    }

    @Test
    fun testPlayProgrammeVod_setsVodPlaybackState() {
        val vm = PlayerViewModel()
        val channel = channels[0].copy(catchup = "append")
        val prog = EpgProgramme(
            channelId = "ch1",
            title = "Special Report",
            startEpochMillis = 1700000000000L,
            stopEpochMillis = 1700003600000L,
            description = "Detailed report"
        )

        vm.playProgrammeVod(prog, channel, channels, matcher)

        val state = vm.uiState.value
        assertTrue(state.isVodPlayback)
        assertEquals("Special Report", state.currentChannel?.name)
        assertTrue(state.currentChannel?.isVod == true)
        assertEquals(prog, state.activeProgramme)
        assertTrue(state.currentChannel?.streamUrl?.contains("utc=1700000000") == true)
        assertEquals(3600000L, state.vodDurationMs)
        assertEquals(0L, state.vodProgressMs)
    }

    @Test
    fun testUpdateVodProgress() {
        val vm = PlayerViewModel()
        vm.updateVodProgress(15000L, 60000L)

        assertEquals(15000L, vm.uiState.value.vodProgressMs)
        assertEquals(60000L, vm.uiState.value.vodDurationMs)
    }

    @Test
    fun testLiveBufferProgress_tracksTimeShiftAndLiveEdge() {
        val vm = PlayerViewModel()
        vm.playChannel(channels[0], channels, matcher)

        // Initial state
        assertTrue(vm.uiState.value.isAtLiveEdge)
        assertFalse(vm.uiState.value.canGoBackToStart)
        assertEquals(0L, vm.uiState.value.liveSessionDurationMs)

        // User watched 30 seconds, playing at live edge (offset <= 3s)
        vm.updateLiveBufferProgress(sessionDurationMs = 30000L, positionFromStartMs = 29000L)
        assertEquals(30000L, vm.uiState.value.liveSessionDurationMs)
        assertEquals(29000L, vm.uiState.value.livePositionFromStartMs)
        assertEquals(1000L, vm.uiState.value.timeShiftOffsetMs)
        assertTrue(vm.uiState.value.isAtLiveEdge)
        assertTrue(vm.uiState.value.canGoBackToStart)

        // User scrubs back 20 seconds (position is 10s from start)
        vm.updateLiveBufferProgress(sessionDurationMs = 30000L, positionFromStartMs = 10000L)
        assertEquals(10000L, vm.uiState.value.livePositionFromStartMs)
        assertEquals(20000L, vm.uiState.value.timeShiftOffsetMs)
        assertFalse(vm.uiState.value.isAtLiveEdge) // In time-shift mode!
        assertTrue(vm.uiState.value.canGoBackToStart)

        // User goes back to starting point (0s)
        vm.updateLiveBufferProgress(sessionDurationMs = 30000L, positionFromStartMs = 0L)
        assertEquals(0L, vm.uiState.value.livePositionFromStartMs)
        assertEquals(30000L, vm.uiState.value.timeShiftOffsetMs)
        assertFalse(vm.uiState.value.isAtLiveEdge)

        // Reset live buffer
        vm.resetLiveBuffer()
        assertEquals(0L, vm.uiState.value.liveSessionDurationMs)
        assertEquals(0L, vm.uiState.value.livePositionFromStartMs)
        assertTrue(vm.uiState.value.isAtLiveEdge)
        assertFalse(vm.uiState.value.canGoBackToStart)
    }

    @Test
    fun testCurrentScheduleAndProgrammeLookup() {
        val vm = PlayerViewModel()
        vm.playChannel(channels[0], channels, matcher)

        // Verify currentSchedule is populated
        val schedule = vm.uiState.value.currentSchedule
        assertEquals(1, schedule.size)
        assertEquals("Show 1", schedule[0].title)

        // Lookup at timestamp within programme
        val matchedProg = vm.getProgrammeAtTime(1500L)
        assertNotNull(matchedProg)
        assertEquals("Show 1", matchedProg?.title)

        // Lookup at timestamp outside programme
        val outsideProg = vm.getProgrammeAtTime(5000L)
        assertEquals(null, outsideProg)
    }

    @Test
    fun testGetProgrammeAtTime_cachingBehaviour() {
        val multiProgData = EpgData(
            channels = mapOf("ch1" to EpgChannel("ch1", "Channel 1")),
            programmes = listOf(
                EpgProgramme("ch1", "Show A", 1000L, 2000L),
                EpgProgramme("ch1", "Show B", 2000L, 3000L)
            )
        )
        val testMatcher = EpgMatcher(multiProgData)
        val vm = PlayerViewModel()
        vm.playChannel(channels[0], channels, testMatcher)

        // First lookup hits Show A
        val first = vm.getProgrammeAtTime(1200L)
        assertEquals("Show A", first?.title)

        // Repeated lookups within Show A hit the fast-path cache
        val second = vm.getProgrammeAtTime(1800L)
        assertEquals(first, second)

        // Scrubbing forward to Show B transitions the cache
        val third = vm.getProgrammeAtTime(2500L)
        assertEquals("Show B", third?.title)

        // Repeated lookups within Show B hit the cache
        val fourth = vm.getProgrammeAtTime(2900L)
        assertEquals(third, fourth)
    }

    private fun createTestBitmap(): Bitmap {
        val field = sun.misc.Unsafe::class.java.getDeclaredField("theUnsafe")
        field.isAccessible = true
        val unsafe = field.get(null) as sun.misc.Unsafe
        return unsafe.allocateInstance(Bitmap::class.java) as Bitmap
    }

    @Test
    fun testSaveAndGetChannelThumbnail() {
        val vm = PlayerViewModel()
        val dummyBitmap = createTestBitmap()

        vm.saveChannelThumbnail("ch1", dummyBitmap)

        assertEquals(dummyBitmap, vm.getChannelThumbnail("ch1"))
        assertEquals(1, vm.channelThumbnails.value.size)
        assertTrue(vm.channelThumbnails.value.containsKey("ch1"))
    }

    @Test
    fun testChannelThumbnails_cappedAt20() {
        val vm = PlayerViewModel()
        for (i in 1..25) {
            val bmp = createTestBitmap()
            vm.saveChannelThumbnail("channel_$i", bmp)
        }

        val map = vm.channelThumbnails.value
        assertEquals(20, map.size)
        // Earliest entries should have been removed
        assertFalse(map.containsKey("channel_1"))
        assertTrue(map.containsKey("channel_25"))
    }

    @Test
    fun testPlayChannel_gracefullyHandlesSwitchFrameCapture() {
        val vm = PlayerViewModel()
        vm.playChannel(channels[0], channels, matcher)
        assertEquals("Channel 1", vm.uiState.value.currentChannel?.name)

        // Switching channel without active PlayerView doesn't crash
        vm.playChannel(channels[1], channels, matcher)
        assertEquals("Channel 2", vm.uiState.value.currentChannel?.name)
    }
}
