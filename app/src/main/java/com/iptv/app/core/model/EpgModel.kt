package com.iptv.app.core.model

data class EpgChannel(
    val id: String,
    val displayName: String,
    val iconUrl: String? = null
)

data class EpgProgramme(
    val channelId: String,
    val title: String,
    val startEpochMillis: Long,
    val stopEpochMillis: Long,
    val description: String? = null,
    val category: String? = null,
    val iconUrl: String? = null
) {
    fun isLive(timestampMillis: Long): Boolean {
        return timestampMillis in startEpochMillis until stopEpochMillis
    }

    fun progress(timestampMillis: Long): Float {
        if (stopEpochMillis <= startEpochMillis) return 0f
        if (timestampMillis <= startEpochMillis) return 0f
        if (timestampMillis >= stopEpochMillis) return 1f
        return (timestampMillis - startEpochMillis).toFloat() / (stopEpochMillis - startEpochMillis).toFloat()
    }
}

data class EpgData(
    val channels: Map<String, EpgChannel> = emptyMap(),
    val programmes: List<EpgProgramme> = emptyList()
)
