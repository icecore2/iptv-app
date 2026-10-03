package com.iptv.app.core.metadata

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TitleCleanerTest {

    @Test
    fun `clean title strips resolution and bracket tags`() {
        val result = TitleCleaner.clean("The Dark Knight [1080p] [FHD] (2008)")
        assertEquals("The Dark Knight", result.title)
        assertEquals(2008, result.year)
    }

    @Test
    fun `clean title strips 4K UHD HDR and codec tags`() {
        val result = TitleCleaner.clean("Interstellar 4K UHD HDR HEVC x265 (2014)")
        assertEquals("Interstellar", result.title)
        assertEquals(2014, result.year)
    }

    @Test
    fun `clean title handles Hebrew and English slash titles`() {
        val result = TitleCleaner.clean("פאודה / Fauda S02E05 [1080p]")
        assertEquals("פאודה / Fauda", result.title)
    }

    @Test
    fun `clean title extracts standalone 4-digit year hint`() {
        val result = TitleCleaner.clean("Avatar The Way of Water 2022 1080p BluRay")
        assertEquals("Avatar The Way of Water", result.title)
        assertEquals(2022, result.year)
    }

    @Test
    fun `clean title preserves title when no year hint exists`() {
        val result = TitleCleaner.clean("Breaking Bad")
        assertEquals("Breaking Bad", result.title)
        assertNull(result.year)
    }

    @Test
    fun `clean title strips season and episode designations`() {
        val result = TitleCleaner.clean("Game of Thrones S08E06 [1080p] [x264]")
        assertEquals("Game of Thrones", result.title)
    }
}
