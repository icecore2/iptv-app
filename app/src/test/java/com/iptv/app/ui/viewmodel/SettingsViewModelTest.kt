package com.iptv.app.ui.viewmodel

import com.iptv.app.data.InMemorySettingsRepository
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var repo: InMemorySettingsRepository
    private lateinit var viewModel: SettingsViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        repo = InMemorySettingsRepository()
        viewModel = SettingsViewModel(repo)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun testInitialSettings() = runTest {
        advanceUntilIdle()
        val s = viewModel.settings.value
        assertTrue(s.showChannelLogos)
        assertTrue(s.enablePagination)
        assertEquals(50, s.pageSize)
        assertEquals(15, s.bufferDurationSeconds)
        assertTrue(s.keepScreenOn)
    }

    @Test
    fun testToggleChannelLogos() = runTest {
        viewModel.toggleChannelLogos(false)
        advanceUntilIdle()
        assertFalse(viewModel.settings.value.showChannelLogos)

        viewModel.toggleChannelLogos(true)
        advanceUntilIdle()
        assertTrue(viewModel.settings.value.showChannelLogos)
    }

    @Test
    fun testTogglePaginationAndPageSize() = runTest {
        viewModel.togglePagination(false)
        advanceUntilIdle()
        assertFalse(viewModel.settings.value.enablePagination)

        viewModel.setPageSize(100)
        advanceUntilIdle()
        assertEquals(100, viewModel.settings.value.pageSize)
    }

    @Test
    fun testPlayerSettings_bufferAndAspect() = runTest {
        viewModel.setBufferDuration(60)
        advanceUntilIdle()
        assertEquals(60, viewModel.settings.value.bufferDurationSeconds)

        viewModel.setBufferStorageLimit(2048)
        advanceUntilIdle()
        assertEquals(2048, viewModel.settings.value.bufferStorageLimitMb)

        viewModel.setDefaultAspectRatio(AspectRatioMode.FILL)
        advanceUntilIdle()
        assertEquals(AspectRatioMode.FILL, viewModel.settings.value.defaultAspectRatio)
    }

    @Test
    fun testToggles_hardwareAndScreenWake() = runTest {
        viewModel.toggleKeepScreenOn(false)
        advanceUntilIdle()
        assertFalse(viewModel.settings.value.keepScreenOn)

        viewModel.toggleHardwareAcceleration(false)
        advanceUntilIdle()
        assertFalse(viewModel.settings.value.hardwareAcceleration)

        viewModel.toggleStreamInfoOverlay(true)
        advanceUntilIdle()
        assertTrue(viewModel.settings.value.showStreamInfoOverlay)

        viewModel.toggleAutoLoadLastPlaylist(true)
        advanceUntilIdle()
        assertTrue(viewModel.settings.value.autoLoadLastPlaylist)
    }

    @Test
    fun testResetToDefaults() = runTest {
        viewModel.toggleChannelLogos(false)
        viewModel.setBufferDuration(30)
        advanceUntilIdle()

        viewModel.resetToDefaults()
        advanceUntilIdle()

        assertTrue(viewModel.settings.value.showChannelLogos)
        assertEquals(15, viewModel.settings.value.bufferDurationSeconds)
    }
}
