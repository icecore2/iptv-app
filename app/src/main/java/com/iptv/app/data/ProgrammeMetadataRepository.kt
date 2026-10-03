package com.iptv.app.data

import com.iptv.app.core.metadata.CleanedTitle
import com.iptv.app.core.metadata.MetadataSource
import com.iptv.app.core.metadata.ProgrammeMetadata
import com.iptv.app.core.metadata.TitleCleaner
import com.iptv.app.core.metadata.providers.ImdbMetadataProvider
import com.iptv.app.core.metadata.providers.MetadataProvider
import com.iptv.app.core.metadata.providers.SratimMetadataProvider
import com.iptv.app.core.metadata.providers.TraktMetadataProvider
import com.iptv.app.core.metadata.providers.TvdbMetadataProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import java.util.Collections
import java.util.LinkedHashMap

class ProgrammeMetadataRepository(
    private val imdbProvider: ImdbMetadataProvider = ImdbMetadataProvider(),
    private val sratimProvider: SratimMetadataProvider = SratimMetadataProvider(),
    private val traktProvider: TraktMetadataProvider = TraktMetadataProvider(),
    private val tvdbProvider: TvdbMetadataProvider = TvdbMetadataProvider()
) {

    private val providers: Map<MetadataSource, MetadataProvider> = mapOf(
        MetadataSource.IMDB to imdbProvider,
        MetadataSource.SRATIM to sratimProvider,
        MetadataSource.TRAKT to traktProvider,
        MetadataSource.TVDB to tvdbProvider
    )

    // Cache holding up to 500 metadata entries in memory
    private val cache: MutableMap<String, ProgrammeMetadata> = Collections.synchronizedMap(
        object : LinkedHashMap<String, ProgrammeMetadata>(500, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, ProgrammeMetadata>?): Boolean {
                return size > 500
            }
        }
    )

    private fun cacheKey(source: MetadataSource, language: String, title: String): String {
        return "${source.key}_${language.lowercase()}_${title.trim().lowercase()}"
    }

    fun getCached(rawTitle: String, source: MetadataSource, language: String): ProgrammeMetadata? {
        val cleaned = TitleCleaner.clean(rawTitle)
        return cache.get(cacheKey(source, language, cleaned.title))
    }

    suspend fun resolveMetadata(
        rawTitle: String,
        preferredSource: MetadataSource = MetadataSource.AUTO,
        language: String = "en"
    ): Result<ProgrammeMetadata?> = withContext(Dispatchers.IO) {
        val cleaned = TitleCleaner.clean(rawTitle)
        if (cleaned.title.isBlank()) return@withContext Result.success(null)

        // Check cache first for preferred source
        if (preferredSource != MetadataSource.AUTO) {
            val cached = cache.get(cacheKey(preferredSource, language, cleaned.title))
            if (cached != null) return@withContext Result.success(cached)
        } else {
            // Check any cached entry
            for (src in MetadataSource.CONCRETE_SOURCES) {
                val cached = cache.get(cacheKey(src, language, cleaned.title))
                if (cached != null) return@withContext Result.success(cached)
            }
        }

        // Determine priority order
        val priorityList = getPriorityOrder(cleaned, preferredSource, language)

        for (source in priorityList) {
            val provider = providers[source] ?: continue
            val result = provider.search(
                query = cleaned.title,
                year = cleaned.year,
                language = language
            ).getOrNull()

            if (result != null) {
                cache.put(cacheKey(source, language, cleaned.title), result)
                return@withContext Result.success(result)
            }
        }

        Result.success(null)
    }

    suspend fun resolveAllSources(
        rawTitle: String,
        language: String = "en"
    ): Map<MetadataSource, ProgrammeMetadata> = coroutineScope {
        val cleaned = TitleCleaner.clean(rawTitle)
        if (cleaned.title.isBlank()) return@coroutineScope emptyMap()

        val results = mutableMapOf<MetadataSource, ProgrammeMetadata>()

        // Concurrently query all 4 concrete providers
        val deferredMap = MetadataSource.CONCRETE_SOURCES.associateWith { source ->
            val provider = providers[source]
            async(Dispatchers.IO) {
                val cached = cache.get(cacheKey(source, language, cleaned.title))
                if (cached != null) return@async cached

                val meta = provider?.search(
                    query = cleaned.title,
                    year = cleaned.year,
                    language = language
                )?.getOrNull()

                if (meta != null) {
                    cache.put(cacheKey(source, language, cleaned.title), meta)
                }
                meta
            }
        }

        for ((source, deferred) in deferredMap) {
            deferred.await()?.let { meta ->
                results[source] = meta
            }
        }

        results
    }

    private fun getPriorityOrder(
        cleaned: CleanedTitle,
        preferredSource: MetadataSource,
        language: String
    ): List<MetadataSource> {
        if (preferredSource != MetadataSource.AUTO && providers.containsKey(preferredSource)) {
            val others = MetadataSource.CONCRETE_SOURCES.filter { it != preferredSource }
            return listOf(preferredSource) + others
        }

        // Auto smart ordering:
        val hasHebrew = Regex("""[\u0590-\u05FF]""").containsMatchIn(cleaned.title)
        val isHebrewLang = language.equals("he", ignoreCase = true) || language.equals("heb", ignoreCase = true)

        return if (hasHebrew || isHebrewLang) {
            listOf(MetadataSource.SRATIM, MetadataSource.IMDB, MetadataSource.TVDB, MetadataSource.TRAKT)
        } else {
            listOf(MetadataSource.IMDB, MetadataSource.TRAKT, MetadataSource.TVDB, MetadataSource.SRATIM)
        }
    }
}
