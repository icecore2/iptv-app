package com.iptv.app.core.metadata

data class CleanedTitle(
    val title: String,
    val year: Int? = null
)

object TitleCleaner {

    private val YEAR_REGEX = Regex("""(?:\(|\[|\b)((?:19|20)\d{2})(?:\)|\]|\b)""")
    private val BRACKETED_NOISE = Regex("""\[[^\]]*\]|\([^\)]*\)""")
    private val QUALITY_AND_TAGS = Regex("""(?i)\b(4k|uhd|fhd|hd|sd|hevc|x264|x265|1080p|720p|live|replay|dubbed|subbed|vod|hdr|bluray|web-dl|webrip|dvdrip|remux|hdtv)\b""")
    private val SEASON_EPISODE_REGEX = Regex("""(?i)\b(s\d{1,2}\s*e\d{1,2}|season\s*\d+|episode\s*\d+)\b.*$""")
    private val MULTI_SPACE = Regex("""\s+""")

    fun clean(rawTitle: String): CleanedTitle {
        if (rawTitle.isBlank()) return CleanedTitle("")

        // 1. Extract year if present
        val yearMatch = YEAR_REGEX.findAll(rawTitle).mapNotNull {
            it.groupValues[1].toIntOrNull()
        }.firstOrNull { it in 1900..2099 }

        var cleaned = rawTitle

        // 2. Remove episode / season trailing info
        cleaned = SEASON_EPISODE_REGEX.replace(cleaned, "")

        // 3. Remove bracketed blocks like [HEB], [EN], [1080p], (2024)
        cleaned = BRACKETED_NOISE.replace(cleaned, " ")

        // 4. Remove standalone year if extracted
        if (yearMatch != null) {
            cleaned = cleaned.replace(Regex("""\b$yearMatch\b"""), " ")
        }

        // 5. Remove quality tags and common IPTV descriptors
        cleaned = QUALITY_AND_TAGS.replace(cleaned, " ")

        // 6. Clean common punctuation noise like leading | or -
        cleaned = cleaned.replace(Regex("""^[\s\-|:—]+|[\s\-|:—]+$"""), "")

        // 7. Normalize whitespace
        cleaned = MULTI_SPACE.replace(cleaned, " ").trim()

        // Fallback: if cleaning stripped everything, revert to rawTitle with basic trimming
        if (cleaned.isBlank()) {
            cleaned = rawTitle.trim()
        }

        return CleanedTitle(
            title = cleaned,
            year = yearMatch
        )
    }
}
