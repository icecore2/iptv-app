package com.iptv.app.core.parser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant

class XmlTvDateParserTest {

    @Test
    fun testParseXmlTvDate_withPositiveTimezoneOffset() {
        // 2026-09-22 14:30:00 +0200 is 12:30:00 UTC
        val result = XmlTvDateParser.parseToEpochMillis("20260922143000 +0200")
        assertNotNull(result)
        val instant = Instant.ofEpochMilli(result!!)
        assertEquals(Instant.parse("2026-09-22T12:30:00Z"), instant)
    }

    @Test
    fun testParseXmlTvDate_withNegativeTimezoneOffset() {
        // 2026-09-22 08:30:00 -0400 is 12:30:00 UTC
        val result = XmlTvDateParser.parseToEpochMillis("20260922083000 -0400")
        assertNotNull(result)
        val instant = Instant.ofEpochMilli(result!!)
        assertEquals(Instant.parse("2026-09-22T12:30:00Z"), instant)
    }

    @Test
    fun testParseXmlTvDate_withUtcZ() {
        val result = XmlTvDateParser.parseToEpochMillis("20260922123000 Z")
        assertNotNull(result)
        val instant = Instant.ofEpochMilli(result!!)
        assertEquals(Instant.parse("2026-09-22T12:30:00Z"), instant)
    }

    @Test
    fun testParseXmlTvDate_withoutSpaceBeforeOffset() {
        val result = XmlTvDateParser.parseToEpochMillis("20260922123000+0000")
        assertNotNull(result)
        val instant = Instant.ofEpochMilli(result!!)
        assertEquals(Instant.parse("2026-09-22T12:30:00Z"), instant)
    }

    @Test
    fun testParseXmlTvDate_withoutOffset_defaultsToUtc() {
        val result = XmlTvDateParser.parseToEpochMillis("20260922123000")
        assertNotNull(result)
        val instant = Instant.ofEpochMilli(result!!)
        assertEquals(Instant.parse("2026-09-22T12:30:00Z"), instant)
    }

    @Test
    fun testParseXmlTvDate_invalidInput_returnsNull() {
        assertNull(XmlTvDateParser.parseToEpochMillis("invalid-date"))
        assertNull(XmlTvDateParser.parseToEpochMillis(""))
        assertNull(XmlTvDateParser.parseToEpochMillis(null))
    }
}
