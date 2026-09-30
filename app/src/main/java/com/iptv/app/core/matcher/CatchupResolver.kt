package com.iptv.app.core.matcher

import com.iptv.app.core.model.EpgProgramme
import com.iptv.app.core.model.M3uItem
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

object CatchupResolver {

    private val xcTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd:HH-mm")
        .withZone(ZoneId.of("UTC"))

    fun buildVodUrl(
        channel: M3uItem,
        programme: EpgProgramme,
        currentEpochMillis: Long = System.currentTimeMillis()
    ): String {
        // 1. Direct VOD item (mp4, mkv) where streamUrl is a static file
        if (channel.isVod && !channel.isCatchup &&
            (channel.streamUrl.endsWith(".mp4", ignoreCase = true) ||
             channel.streamUrl.endsWith(".mkv", ignoreCase = true))
        ) {
            return channel.streamUrl
        }

        val startSec = programme.startEpochMillis / 1000
        val stopSec = programme.stopEpochMillis / 1000
        val durationSec = (stopSec - startSec).coerceAtLeast(60)
        val offsetSec = ((currentEpochMillis - programme.startEpochMillis) / 1000).coerceAtLeast(0)
        val nowSec = currentEpochMillis / 1000

        // 2. Custom template in catchupSource
        val template = channel.catchupSource
        if (!template.isNullOrBlank()) {
            val resolved = template
                .replace("\${start}", startSec.toString())
                .replace("{utc}", startSec.toString())
                .replace("\${end}", stopSec.toString())
                .replace("\${stop}", stopSec.toString())
                .replace("\${lutc}", stopSec.toString())
                .replace("\${timestamp}", nowSec.toString())
                .replace("\${duration}", durationSec.toString())
                .replace("\${offset}", offsetSec.toString())
                .replace("\${catchup-id}", channel.tvgId ?: channel.id)

            return if (resolved.startsWith("http://", ignoreCase = true) ||
                resolved.startsWith("https://", ignoreCase = true)
            ) {
                resolved
            } else if (resolved.startsWith("?")) {
                val sep = if (channel.streamUrl.contains("?")) "&" else "?"
                "${channel.streamUrl.substringBefore("?")}$sep${resolved.removePrefix("?")}"
            } else {
                val sep = if (channel.streamUrl.contains("?")) "&" else "?"
                "${channel.streamUrl}$sep$resolved"
            }
        }

        // 3. Known catchup types
        val catchupType = channel.catchup?.lowercase()
        when (catchupType) {
            "flussonic", "fs" -> {
                val baseUrl = channel.streamUrl.substringBeforeLast("/")
                return "$baseUrl/timeshift_abs-$startSec.m3u8"
            }
            "xc" -> {
                // Xtream Codes timeshift format:
                // /timeshift/username/password/durationMinutes/YYYY-MM-DD:HH-mm/stream_id.ext
                val regex = Regex(".*/live/([^/]+)/([^/]+)/([^/.]+)(\\.[a-zA-Z0-9]+)?")
                val match = regex.find(channel.streamUrl)
                if (match != null) {
                    val (user, pass, streamId, ext) = match.destructured
                    val startTimeFormatted = xcTimeFormatter.format(Instant.ofEpochMilli(programme.startEpochMillis))
                    val durationMin = durationSec / 60
                    val extension = if (ext.isNotEmpty()) ext else ".ts"
                    val host = channel.streamUrl.substringBefore("/live/")
                    return "$host/timeshift/$user/$pass/$durationMin/$startTimeFormatted/$streamId$extension"
                }
            }
        }

        // 4. Default append mode / fallback for IPTV streams
        val sep = if (channel.streamUrl.contains("?")) "&" else "?"
        return "${channel.streamUrl}${sep}utc=$startSec&lutc=$stopSec"
    }

    fun hasCatchupSupport(channel: M3uItem): Boolean {
        return channel.catchup != null || channel.catchupSource != null || channel.isVod
    }
}
