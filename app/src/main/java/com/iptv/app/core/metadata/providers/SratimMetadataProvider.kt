package com.iptv.app.core.metadata.providers

import com.iptv.app.core.metadata.MetadataSource
import com.iptv.app.core.metadata.ProgrammeMetadata
import com.iptv.app.data.NetworkClient
import com.iptv.app.data.OkHttpNetworkClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

class SratimMetadataProvider(
    private val networkClient: NetworkClient = OkHttpNetworkClient()
) : MetadataProvider {

    override val source: MetadataSource = MetadataSource.SRATIM

    companion object {
        private const val BASE_URL = "https://www.sratim.co.il"
        private val RESULT_ITEM_REGEX = Regex(
            """<a\s+href="(/tt\d+/)">.*?<div\s+class="search_result_title_cover_img"[^>]*style="background:url\('([^']+)'\);".*?<div\s+class="search_result_title_cover_info_name">([^<]+)</div>(?:.*?<div\s+class="search_result_title_cover_icon">(\d{4})</div>)?""",
            setOf(RegexOption.DOT_MATCHES_ALL)
        )
        private val JSON_LD_REGEX = Regex(
            """<script[^>]*type="application/ld\+json"[^>]*>\s*([\{\[].*?[\}\]])\s*</script>""",
            setOf(RegexOption.DOT_MATCHES_ALL)
        )
        private val YOUTUBE_REGEX = Regex(
            """(?:youtube\.com/(?:watch\?v=|embed/)|youtu\.be/)([a-zA-Z0-9_\-]{11})"""
        )
    }

    override suspend fun search(query: String, year: Int?, language: String): Result<ProgrammeMetadata?> =
        withContext(Dispatchers.IO) {
            try {
                val cleanQuery = query.trim()
                if (cleanQuery.isBlank()) return@withContext Result.success(null)

                val html = networkClient.postForm(
                    url = "$BASE_URL/search.php",
                    formParams = mapOf("q" to cleanQuery),
                    headers = mapOf(
                        "Referer" to "$BASE_URL/",
                        "X-Requested-With" to "XMLHttpRequest"
                    )
                ).bufferedReader(Charsets.UTF_8).use { it.readText() }

                val matches = RESULT_ITEM_REGEX.findAll(html).toList()
                if (matches.isEmpty()) {
                    // Try simple href match if detailed regex misses due to variations in html layout
                    val simpleHrefMatch = Regex("""<a\s+[^>]*href="(/tt\d+[^"]*)"""").find(html)
                    if (simpleHrefMatch == null) return@withContext Result.success(null)

                    val href = simpleHrefMatch.groupValues[1]
                    return@withContext Result.success(fetchDetails(href, cleanQuery, year))
                }

                // Pick the match closest to year if year was provided
                var bestMatch = matches.first()
                if (year != null) {
                    for (m in matches) {
                        val mYear = m.groupValues.getOrNull(4)?.toIntOrNull()
                        if (mYear != null && kotlin.math.abs(mYear - year) <= 1) {
                            bestMatch = m
                            break
                        }
                    }
                }

                val href = bestMatch.groupValues[1]
                val thumbUrl = bestMatch.groupValues[2]
                val itemTitle = bestMatch.groupValues[3]
                val itemYear = bestMatch.groupValues.getOrNull(4)?.toIntOrNull() ?: year

                val enriched = fetchDetails(href, itemTitle, itemYear)
                val finalPoster = enriched?.posterUrl?.takeIf { it.isNotBlank() } ?: thumbUrl.takeIf { it.isNotBlank() }

                Result.success(
                    enriched?.copy(
                        posterUrl = finalPoster,
                        year = enriched.year ?: itemYear
                    ) ?: ProgrammeMetadata(
                        source = MetadataSource.SRATIM,
                        title = itemTitle,
                        year = itemYear,
                        posterUrl = thumbUrl,
                        language = "he",
                        externalId = href.trim('/'),
                        webUrl = "$BASE_URL$href"
                    )
                )
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    private suspend fun fetchDetails(href: String, fallbackTitle: String, fallbackYear: Int?): ProgrammeMetadata? {
        return try {
            val pageHtml = networkClient.openStream("$BASE_URL$href").bufferedReader(Charsets.UTF_8).use { it.readText() }

            var desc: String? = null
            var poster: String? = null
            var rating: Double? = null
            var releaseDate: String? = null
            var title = fallbackTitle
            var genres = emptyList<String>()
            var director: String? = null
            var cast = mutableListOf<String>()

            // Find JSON-LD script tag
            for (match in JSON_LD_REGEX.findAll(pageHtml)) {
                val jsonStr = match.groupValues[1]
                val trimmedJson = jsonStr.trim()
                val objects: List<JSONObject> = try {
                    if (trimmedJson.startsWith("[")) {
                        val arr = JSONArray(trimmedJson)
                        (0 until arr.length()).mapNotNull { arr.optJSONObject(it) }
                    } else {
                        val root = JSONObject(trimmedJson)
                        if (root.has("@graph")) {
                            val graph = root.optJSONArray("@graph")
                            (0 until (graph?.length() ?: 0)).mapNotNull { graph?.optJSONObject(it) }
                        } else {
                            listOf(root)
                        }
                    }
                } catch (_: Exception) {
                    continue
                }

                for (obj in objects) {
                    val type = obj.optString("@type")
                    if (type.equals("Movie", ignoreCase = true) || type.equals("TVSeries", ignoreCase = true)) {
                        title = obj.optString("name", fallbackTitle)
                        desc = obj.optString("description").takeIf { it.isNotBlank() }
                        poster = obj.optString("image").takeIf { it.isNotBlank() }
                        releaseDate = obj.optString("dateCreated").takeIf { it.isNotBlank() }
                            ?: obj.optString("datePublished").takeIf { it.isNotBlank() }

                        val ratingObj = obj.optJSONObject("aggregateRating")
                        if (ratingObj != null) {
                            val rVal = ratingObj.optDouble("ratingValue", 0.0).takeIf { it > 0.0 }
                                ?: ratingObj.optString("ratingValue").toDoubleOrNull()
                            if (rVal != null && rVal > 0.0) rating = rVal
                        }

                        val genreStr = obj.optString("genre")
                        if (genreStr.isNotBlank()) {
                            genres = genreStr.split(",").map { it.trim() }
                        }

                        val dirObj = obj.optJSONArray("director")
                        if (dirObj != null && dirObj.length() > 0) {
                            director = dirObj.optJSONObject(0)?.optString("name")
                        }

                        val actObj = obj.optJSONArray("actor")
                        if (actObj != null) {
                            for (a in 0 until actObj.length()) {
                                actObj.optJSONObject(a)?.optString("name")?.let { actorName ->
                                    if (actorName.isNotBlank()) cast.add(actorName)
                                }
                            }
                        }
                        break
                    }
                }
                if (desc != null || poster != null) break
            }

            // Extract trailer YouTube ID if present on page
            val trailerYtId = YOUTUBE_REGEX.find(pageHtml)?.groupValues?.getOrNull(1)

            val parsedYear = releaseDate?.take(4)?.toIntOrNull() ?: fallbackYear
            val extId = Regex("""(tt\d+)""").find(href)?.groupValues?.get(1) ?: href.trim('/')

            ProgrammeMetadata(
                source = MetadataSource.SRATIM,
                title = title,
                year = parsedYear,
                releaseDate = releaseDate,
                rating = rating,
                description = desc,
                language = "he",
                posterUrl = poster,
                genres = genres,
                director = director,
                cast = cast,
                trailerYoutubeId = trailerYtId,
                trailerUrl = trailerYtId?.let { "https://www.youtube.com/watch?v=$it" },
                externalId = extId,
                webUrl = "$BASE_URL$href"
            )
        } catch (_: Exception) {
            null
        }
    }
}
