package com.iptv.app.core.parser

import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.regex.Pattern

object XmlTvDateParser {

    private val pattern = Pattern.compile(
        "^(\\d{4})(\\d{2})(\\d{2})(\\d{2})(\\d{2})(\\d{2})(?:\\s*([+-]\\d{2}:?\\d{2}|Z))?"
    )

    fun parseToEpochMillis(dateStr: String?): Long? {
        if (dateStr.isNullOrBlank()) return null
        val trimmed = dateStr.trim()
        val matcher = pattern.matcher(trimmed)
        if (!matcher.find()) return null

        return try {
            val year = matcher.group(1)!!.toInt()
            val month = matcher.group(2)!!.toInt()
            val day = matcher.group(3)!!.toInt()
            val hour = matcher.group(4)!!.toInt()
            val minute = matcher.group(5)!!.toInt()
            val second = matcher.group(6)!!.toInt()

            val tzStr = matcher.group(7)
            val offset = parseZoneOffset(tzStr)

            OffsetDateTime.of(year, month, day, hour, minute, second, 0, offset)
                .toInstant()
                .toEpochMilli()
        } catch (_: Exception) {
            null
        }
    }

    private fun parseZoneOffset(tzStr: String?): ZoneOffset {
        if (tzStr == null || tzStr == "Z" || tzStr.isEmpty()) {
            return ZoneOffset.UTC
        }
        val clean = tzStr.replace(":", "")
        return if (clean.length == 5) {
            // e.g. "+0200" -> "+02:00"
            val formatted = "${clean.substring(0, 3)}:${clean.substring(3)}"
            ZoneOffset.of(formatted)
        } else {
            ZoneOffset.of(clean)
        }
    }
}
