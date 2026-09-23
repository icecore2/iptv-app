package com.iptv.app.ui.viewmodel

import com.iptv.app.core.matcher.EpgMatcher
import com.iptv.app.core.model.EpgChannel
import com.iptv.app.core.model.EpgData
import com.iptv.app.core.model.EpgProgramme
import com.iptv.app.core.model.M3uItem
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
}
