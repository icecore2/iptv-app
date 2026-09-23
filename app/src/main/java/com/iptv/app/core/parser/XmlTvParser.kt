package com.iptv.app.core.parser

import com.iptv.app.core.model.EpgChannel
import com.iptv.app.core.model.EpgData
import com.iptv.app.core.model.EpgProgramme
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.InputStream
import java.io.PushbackInputStream
import java.util.zip.GZIPInputStream

class XmlTvParser {

    private val factory = XmlPullParserFactory.newInstance().apply {
        isNamespaceAware = false
    }

    fun parse(inputStream: InputStream, isGzip: Boolean? = null): EpgData {
        val effectiveStream = try {
            wrapIfGzip(inputStream, isGzip)
        } catch (_: Exception) {
            return EpgData()
        }

        val channels = mutableMapOf<String, EpgChannel>()
        val programmes = mutableListOf<EpgProgramme>()

        try {
            val parser = factory.newPullParser()
            parser.setInput(effectiveStream, "UTF-8")

            var eventType = parser.eventType
            while (eventType != XmlPullParser.END_DOCUMENT) {
                if (eventType == XmlPullParser.START_TAG) {
                    when (parser.name.lowercase()) {
                        "channel" -> parseChannel(parser)?.let { channels[it.id] = it }
                        "programme" -> parseProgramme(parser)?.let { programmes.add(it) }
                    }
                }
                eventType = parser.next()
            }
        } catch (_: Exception) {
            // Silently handle truncated streams or non-XML safely
        } finally {
            try {
                effectiveStream.close()
            } catch (_: Exception) {}
        }

        return EpgData(channels = channels, programmes = programmes)
    }

    private fun parseChannel(parser: XmlPullParser): EpgChannel? {
        val id = parser.getAttributeValue(null, "id") ?: return null
        var displayName = ""
        var iconUrl: String? = null

        var eventType = parser.next()
        while (!(eventType == XmlPullParser.END_TAG && parser.name.equals("channel", ignoreCase = true))) {
            if (eventType == XmlPullParser.START_TAG) {
                when (parser.name.lowercase()) {
                    "display-name" -> displayName = parser.nextText()
                    "icon" -> iconUrl = parser.getAttributeValue(null, "src")
                }
            }
            if (eventType == XmlPullParser.END_DOCUMENT) break
            eventType = parser.next()
        }

        return EpgChannel(
            id = id,
            displayName = if (displayName.isNotBlank()) displayName else id,
            iconUrl = iconUrl
        )
    }

    private fun parseProgramme(parser: XmlPullParser): EpgProgramme? {
        val channelId = parser.getAttributeValue(null, "channel") ?: return null
        val startStr = parser.getAttributeValue(null, "start")
        val stopStr = parser.getAttributeValue(null, "stop")

        val startMillis = XmlTvDateParser.parseToEpochMillis(startStr) ?: 0L
        val stopMillis = XmlTvDateParser.parseToEpochMillis(stopStr) ?: (startMillis + 3600_000L)

        var title = ""
        var desc: String? = null
        var category: String? = null
        var iconUrl: String? = null

        var eventType = parser.next()
        while (!(eventType == XmlPullParser.END_TAG && parser.name.equals("programme", ignoreCase = true))) {
            if (eventType == XmlPullParser.START_TAG) {
                when (parser.name.lowercase()) {
                    "title" -> title = parser.nextText()
                    "desc" -> desc = parser.nextText()
                    "category" -> category = parser.nextText()
                    "icon" -> iconUrl = parser.getAttributeValue(null, "src")
                }
            }
            if (eventType == XmlPullParser.END_DOCUMENT) break
            eventType = parser.next()
        }

        return EpgProgramme(
            channelId = channelId,
            title = if (title.isNotBlank()) title else "No Title",
            startEpochMillis = startMillis,
            stopEpochMillis = stopMillis,
            description = desc,
            category = category,
            iconUrl = iconUrl
        )
    }

    private fun wrapIfGzip(inputStream: InputStream, explicitGzip: Boolean?): InputStream {
        if (explicitGzip == true) {
            return GZIPInputStream(inputStream)
        }

        val pushback = PushbackInputStream(inputStream, 2)
        val header = ByteArray(2)
        val bytesRead = pushback.read(header, 0, 2)
        if (bytesRead > 0) {
            pushback.unread(header, 0, bytesRead)
        }

        val isGzip = bytesRead == 2 &&
                (header[0] == 0x1f.toByte()) &&
                (header[1] == 0x8b.toByte())

        return if (isGzip) GZIPInputStream(pushback) else pushback
    }
}
