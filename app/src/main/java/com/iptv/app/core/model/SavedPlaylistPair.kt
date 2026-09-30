package com.iptv.app.core.model

import java.util.UUID

/**
 * Represents a saved playlist and EPG configuration pair.
 *
 * @param id Unique identifier for the pair
 * @param name User-friendly display label (e.g. "Home Cable", "Sports UK")
 * @param playlistUrl M3U or M3U8 URL (or sample identifier)
 * @param epgUrl Optional XMLTV guide URL (.xml or .xml.gz)
 * @param isSample True if this pair is the built-in sample demo
 * @param createdAt Timestamp when created
 * @param favoriteIds Channel IDs bookmarked as favorites within this playlist
 */
data class SavedPlaylistPair(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val playlistUrl: String,
    val epgUrl: String? = null,
    val isSample: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val favoriteIds: Set<String> = emptySet()
)
