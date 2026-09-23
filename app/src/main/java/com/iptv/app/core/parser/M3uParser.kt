package com.iptv.app.core.parser

import com.iptv.app.core.model.M3uItem
import com.iptv.app.core.model.M3uPlaylist
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.io.StringReader
import java.util.UUID
import java.util.regex.Pattern

class M3uParser {

    private val attributePattern = Pattern.compile("([a-zA-Z0-9_-]+)=(?:\"([^\"]*)\"|'([^']*)'|([^ \\t\\r\\n,]+))")

    fun parse(content: String): M3uPlaylist {
        return parse(BufferedReader(StringReader(content)))
    }

    fun parse(inputStream: InputStream): M3uPlaylist {
        return parse(BufferedReader(InputStreamReader(inputStream, Charsets.UTF_8)))
    }

    fun parse(reader: BufferedReader): M3uPlaylist {
        val items = mutableListOf<M3uItem>()
        val groups = linkedSetOf<String>()
        val headerAttributes = mutableMapOf<String, String>()
        var epgUrl: String? = null

        var currentTvgId: String? = null
        var currentTvgName: String? = null
        var currentTvgLogo: String? = null
        var currentGroup: String? = null
        var currentName: String? = null
        var currentIsRadio = false
        val currentHeaders = mutableMapOf<String, String>()
        var hasPendingItem = false

        reader.useLines { lines ->
            for (rawLine in lines) {
                val line = rawLine.trim()
                if (line.isEmpty()) continue

                if (line.startsWith("#EXTM3U", ignoreCase = true)) {
                    val attrs = parseAttributes(line.substring("#EXTM3U".length))
                    headerAttributes.putAll(attrs)
                    epgUrl = attrs["url-tvg"] ?: attrs["x-tvg-url"] ?: attrs["tvg-url"]
                } else if (line.startsWith("#EXTINF:", ignoreCase = true)) {
                    // Reset pending item state
                    currentHeaders.clear()
                    hasPendingItem = true

                    val contentAfterExtInf = line.substring("#EXTINF:".length).trim()
                    val commaIndex = findChannelTitleSeparator(contentAfterExtInf)

                    val attrsPart = if (commaIndex != -1) contentAfterExtInf.substring(0, commaIndex).trim() else contentAfterExtInf
                    val titlePart = if (commaIndex != -1) contentAfterExtInf.substring(commaIndex + 1).trim() else ""

                    val attrs = parseAttributes(attrsPart)
                    currentTvgId = attrs["tvg-id"]
                    currentTvgName = attrs["tvg-name"]
                    currentTvgLogo = attrs["tvg-logo"]
                    currentGroup = attrs["group-title"]
                    currentIsRadio = attrs["radio"]?.equals("true", ignoreCase = true) == true

                    currentName = when {
                        titlePart.isNotEmpty() -> titlePart
                        !currentTvgName.isNullOrEmpty() -> currentTvgName
                        else -> "Unnamed Channel"
                    }
                } else if (line.startsWith("#EXTGRP:", ignoreCase = true)) {
                    currentGroup = line.substring("#EXTGRP:".length).trim()
                } else if (line.startsWith("#EXTVLCOPT:", ignoreCase = true)) {
                    val opt = line.substring("#EXTVLCOPT:".length).trim()
                    parseVlcOption(opt, currentHeaders)
                } else if (!line.startsWith("#")) {
                    // This is the stream URL
                    if (hasPendingItem) {
                        val groupName = if (!currentGroup.isNullOrBlank()) currentGroup!! else "General"
                        groups.add(groupName)

                        val item = M3uItem(
                            id = currentTvgId ?: UUID.randomUUID().toString(),
                            name = currentName ?: "Channel",
                            streamUrl = line,
                            group = groupName,
                            logoUrl = currentTvgLogo,
                            tvgId = currentTvgId,
                            tvgName = currentTvgName,
                            headers = HashMap(currentHeaders),
                            isRadio = currentIsRadio
                        )
                        items.add(item)
                        hasPendingItem = false
                        currentHeaders.clear()
                    }
                }
            }
        }

        return M3uPlaylist(
            items = items,
            groups = groups.toList(),
            epgUrl = epgUrl,
            headerAttributes = headerAttributes
        )
    }

    private fun findChannelTitleSeparator(text: String): Int {
        var inQuotes = false
        var quoteChar = ' '
        for (i in text.indices) {
            val char = text[i]
            if ((char == '"' || char == '\'') && (i == 0 || text[i - 1] != '\\')) {
                if (!inQuotes) {
                    inQuotes = true
                    quoteChar = char
                } else if (char == quoteChar) {
                    inQuotes = false
                }
            } else if (char == ',' && !inQuotes) {
                return i
            }
        }
        return -1
    }

    private fun parseAttributes(text: String): Map<String, String> {
        val map = mutableMapOf<String, String>()
        val matcher = attributePattern.matcher(text)
        while (matcher.find()) {
            val key = matcher.group(1)?.lowercase() ?: continue
            val value = matcher.group(2) ?: matcher.group(3) ?: matcher.group(4) ?: ""
            map[key] = value
        }
        return map
    }

    private fun parseVlcOption(opt: String, headers: MutableMap<String, String>) {
        val equalsIndex = opt.indexOf('=')
        if (equalsIndex == -1) return
        val key = opt.substring(0, equalsIndex).trim().lowercase()
        val value = opt.substring(equalsIndex + 1).trim()
        when (key) {
            "http-user-agent" -> headers["User-Agent"] = value
            "http-referrer", "http-referer" -> headers["Referer"] = value
        }
    }
}
