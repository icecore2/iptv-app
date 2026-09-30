package com.iptv.app.core.model

data class M3uItem(
    val id: String,
    val name: String,
    val streamUrl: String,
    val group: String = "General",
    val logoUrl: String? = null,
    val tvgId: String? = null,
    val tvgName: String? = null,
    val headers: Map<String, String> = emptyMap(),
    val isRadio: Boolean = false,
    val catchup: String? = null,
    val catchupSource: String? = null,
    val catchupDays: Int? = null,
    val isCatchup: Boolean = false
) {
    val isVod: Boolean
        get() = isCatchup ||
                streamUrl.endsWith(".mp4", ignoreCase = true) ||
                streamUrl.endsWith(".mkv", ignoreCase = true) ||
                group.contains("movie", ignoreCase = true) ||
                group.contains("vod", ignoreCase = true) ||
                group.contains("cinema", ignoreCase = true) ||
                group.contains("film", ignoreCase = true) ||
                group.contains("catchup", ignoreCase = true)
}

data class M3uPlaylist(
    val items: List<M3uItem> = emptyList(),
    val groups: List<String> = emptyList(),
    val epgUrl: String? = null,
    val headerAttributes: Map<String, String> = emptyMap()
)
