package com.iptv.app.data

import com.iptv.app.core.model.AppSettings
import com.iptv.app.ui.viewmodel.AspectRatioMode
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsRepositoryTest {

    @Test
    fun testDefaultSettings() = runTest {
        val repo = InMemorySettingsRepository()
        val settings = repo.getSettings()

        assertTrue(settings.showChannelLogos)
        assertTrue(settings.enablePagination)
        assertEquals(50, settings.pageSize)
        assertTrue(settings.showEpgInList)
        assertFalse(settings.autoLoadLastPlaylist)
        assertEquals(15, settings.bufferDurationSeconds)
        assertEquals(1024, settings.bufferStorageLimitMb)
        assertTrue(settings.keepScreenOn)
        assertTrue(settings.fastChannelSwitching)
        assertTrue(settings.hardwareAcceleration)
        assertEquals(AspectRatioMode.FIT, settings.defaultAspectRatio)
        assertFalse(settings.showStreamInfoOverlay)
    }

    @Test
    fun testUpdateSettings() = runTest {
        val repo = InMemorySettingsRepository()

        repo.updateSettings {
            it.copy(
                showChannelLogos = false,
                pageSize = 100,
                bufferDurationSeconds = 30,
                bufferStorageLimitMb = 2048,
                defaultAspectRatio = AspectRatioMode.ZOOM,
                showStreamInfoOverlay = true
            )
        }

        val updated = repo.getSettings()
        assertFalse(updated.showChannelLogos)
        assertEquals(100, updated.pageSize)
        assertEquals(30, updated.bufferDurationSeconds)
        assertEquals(2048, updated.bufferStorageLimitMb)
        assertEquals(AspectRatioMode.ZOOM, updated.defaultAspectRatio)
        assertTrue(updated.showStreamInfoOverlay)
        assertEquals(updated, repo.settingsFlow.value)
    }

    @Test
    fun testResetToDefaults() = runTest {
        val repo = InMemorySettingsRepository()

        repo.updateSettings {
            it.copy(
                showChannelLogos = false,
                bufferDurationSeconds = 60,
                enablePagination = false
            )
        }

        assertFalse(repo.getSettings().showChannelLogos)
        assertFalse(repo.getSettings().enablePagination)

        repo.resetToDefaults()

        val reset = repo.getSettings()
        assertTrue(reset.showChannelLogos)
        assertTrue(reset.enablePagination)
        assertEquals(15, reset.bufferDurationSeconds)
    }
}
