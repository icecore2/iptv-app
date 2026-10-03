package com.iptv.app.core.metadata

enum class MetadataSource(
    val displayName: String,
    val key: String,
    val brandColorHex: Long
) {
    AUTO("Auto", "auto", 0xFF6366F1),
    IMDB("IMDb", "imdb", 0xFFF5C518),
    TRAKT("Trakt", "trakt", 0xFFED1C24),
    SRATIM("sratim.co.il", "sratim", 0xFF169DFF),
    TVDB("TheTVDB", "tvdb", 0xFF43A047);

    companion object {
        val CONCRETE_SOURCES = listOf(IMDB, TRAKT, SRATIM, TVDB)

        fun fromKey(key: String?): MetadataSource {
            return entries.find { it.key.equals(key, ignoreCase = true) } ?: AUTO
        }
    }
}

data class ProgrammeMetadata(
    val source: MetadataSource,
    val title: String,
    val originalTitle: String? = null,
    val year: Int? = null,
    val releaseDate: String? = null,
    val rating: Double? = null, // e.g. 7.9 (out of 10.0)
    val ratingCount: Int? = null,
    val description: String? = null,
    val language: String? = null,
    val posterUrl: String? = null,
    val backdropUrl: String? = null,
    val genres: List<String> = emptyList(),
    val director: String? = null,
    val cast: List<String> = emptyList(),
    val trailerUrl: String? = null,
    val trailerYoutubeId: String? = null,
    val externalId: String? = null,
    val webUrl: String? = null
) {
    val ratingDisplay: String?
        get() = rating?.let { String.format(java.util.Locale.US, "★ %.1f", it) }

    val hasTrailer: Boolean
        get() = !trailerYoutubeId.isNullOrBlank() || !trailerUrl.isNullOrBlank()

    val youtubeWatchUrl: String?
        get() = when {
            !trailerYoutubeId.isNullOrBlank() -> "https://www.youtube.com/watch?v=$trailerYoutubeId"
            trailerUrl?.contains("youtube.com") == true || trailerUrl?.contains("youtu.be") == true -> trailerUrl
            else -> null
        }
}
