package com.iptv.app.core.matcher

import com.iptv.app.core.model.ChannelWithEpg
import com.iptv.app.core.model.EpgData
import com.iptv.app.core.model.EpgProgramme
import com.iptv.app.core.model.M3uItem

class EpgMatcher(private val epgData: EpgData) {

    private val programmesByChannel: Map<String, List<EpgProgramme>> =
        epgData.programmes.groupBy { it.channelId }
            .mapValues { entry -> entry.value.sortedBy { it.startEpochMillis } }

    private val normalizedChannelIndex: Map<String, String> = buildMap {
        for ((channelId, channel) in epgData.channels) {
            put(channelId.lowercase(), channelId)
            put(normalize(channel.displayName), channelId)
        }
    }

    fun findEpgChannelId(channel: M3uItem): String? {
        // 1. Direct tvgId match
        channel.tvgId?.let { tvgId ->
            if (epgData.channels.containsKey(tvgId)) return tvgId
            val lower = tvgId.lowercase()
            if (normalizedChannelIndex.containsKey(lower)) return normalizedChannelIndex[lower]
        }

        // 2. Direct tvgName normalized match
        channel.tvgName?.let { tvgName ->
            val norm = normalize(tvgName)
            if (normalizedChannelIndex.containsKey(norm)) return normalizedChannelIndex[norm]
        }

        // 3. Channel display name normalized match
        val nameNorm = normalize(channel.name)
        if (normalizedChannelIndex.containsKey(nameNorm)) {
            return normalizedChannelIndex[nameNorm]
        }

        return null
    }

    fun getSchedule(channel: M3uItem): List<EpgProgramme> {
        val epgChannelId = findEpgChannelId(channel) ?: return emptyList()
        return programmesByChannel[epgChannelId] ?: emptyList()
    }

    fun getCurrentProgramme(channel: M3uItem, timestamp: Long = System.currentTimeMillis()): EpgProgramme? {
        val schedule = getSchedule(channel)
        return schedule.firstOrNull { it.isLive(timestamp) }
    }

    fun getNextProgramme(channel: M3uItem, timestamp: Long = System.currentTimeMillis()): EpgProgramme? {
        val schedule = getSchedule(channel)
        val current = getCurrentProgramme(channel, timestamp)
        val cutoff = current?.stopEpochMillis ?: timestamp
        return schedule.firstOrNull { it.startEpochMillis >= cutoff }
    }

    fun enrichChannel(channel: M3uItem, timestamp: Long = System.currentTimeMillis()): ChannelWithEpg {
        val current = getCurrentProgramme(channel, timestamp)
        val next = getNextProgramme(channel, timestamp)
        val progress = current?.progress(timestamp) ?: 0f
        return ChannelWithEpg(
            channel = channel,
            currentProgramme = current,
            nextProgramme = next,
            progress = progress
        )
    }

    private fun normalize(name: String): String {
        return name.lowercase()
            .replace(Regex("\\[.*?\\]|\\(.*?\\)"), "") // Remove bracketed tags like [UK], (US)
            .replace(Regex("\\b(hd|fhd|uhd|4k|sd|hevc|1080p|720p)\\b"), "") // Remove quality keywords
            .replace(Regex("[^a-z0-9]"), "") // Remove spaces, punctuation
            .trim()
    }
}
