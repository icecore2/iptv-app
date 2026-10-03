package com.iptv.app.core.metadata.providers

import com.iptv.app.core.metadata.MetadataSource
import com.iptv.app.core.metadata.ProgrammeMetadata
import com.iptv.app.data.NetworkClient
import com.iptv.app.data.OkHttpNetworkClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.net.URLEncoder

class TraktMetadataProvider(
    private val networkClient: NetworkClient = OkHttpNetworkClient(),
    private val traktClientIdProvider: () -> String = { DEFAULT_CLIENT_ID }
) : MetadataProvider {

    override val source: MetadataSource = MetadataSource.TRAKT

    companion object {
        // Default public demo client ID for Trakt API access
        const val DEFAULT_CLIENT_ID = "63d76e7371d79860b21e84df2448cae5cae1c07223c3325e9854efbdca6fa58a"
    }

    override suspend fun search(query: String, year: Int?, language: String): Result<ProgrammeMetadata?> =
        withContext(Dispatchers.IO) {
            try {
                val cleanQuery = query.trim()
                if (cleanQuery.isBlank()) return@withContext Result.success(null)

                val clientId = traktClientIdProvider().trim().ifBlank { DEFAULT_CLIENT_ID }
                val encodedQuery = URLEncoder.encode(cleanQuery, "UTF-8")
                val url = "https://api.trakt.tv/search/movie,show?query=$encodedQuery&extended=full"

                val jsonResponse = networkClient.openStream(
                    url = url,
                    headers = mapOf(
                        "Content-Type" to "application/json",
                        "trakt-api-version" to "2",
                        "trakt-api-key" to clientId
                    )
                ).bufferedReader(Charsets.UTF_8).use { it.readText() }

                val array = JSONArray(jsonResponse)
                if (array.length() == 0) return@withContext Result.success(null)

                var bestItem: org.json.JSONObject? = null
                var bestType = "movie"

                for (i in 0 until array.length()) {
                    val entry = array.optJSONObject(i) ?: continue
                    val type = entry.optString("type", "movie")
                    val mediaObj = entry.optJSONObject(type) ?: continue

                    if (year != null) {
                        val mYear = mediaObj.optInt("year", 0)
                        if (mYear != 0 && kotlin.math.abs(mYear - year) <= 1) {
                            bestItem = mediaObj
                            bestType = type
                            break
                        }
                    } else {
                        bestItem = mediaObj
                        bestType = type
                        break
                    }
                }

                if (bestItem == null) {
                    val first = array.optJSONObject(0) ?: return@withContext Result.success(null)
                    bestType = first.optString("type", "movie")
                    bestItem = first.optJSONObject(bestType) ?: return@withContext Result.success(null)
                }

                val title = bestItem.optString("title", query)
                val itemYear = bestItem.optInt("year").takeIf { it > 0 } ?: year
                val overview = bestItem.optString("overview").takeIf { it.isNotBlank() }
                val rating = bestItem.optDouble("rating", 0.0).takeIf { it > 0.0 }
                val votes = bestItem.optInt("votes", 0).takeIf { it > 0 }
                val released = bestItem.optString("released").takeIf { it.isNotBlank() }
                val trailerUrl = bestItem.optString("trailer").takeIf { it.isNotBlank() }

                val idsObj = bestItem.optJSONObject("ids")
                val imdbId = idsObj?.optString("imdb")?.takeIf { it.isNotBlank() && it.startsWith("tt") }
                val slug = idsObj?.optString("slug")

                // Extract YouTube ID if trailer is a YouTube URL
                val ytId = trailerUrl?.let { tr ->
                    val m = Regex("""(?:youtube\.com/(?:watch\?v=|embed/)|youtu\.be/)([a-zA-Z0-9_\-]{11})""").find(tr)
                    m?.groupValues?.getOrNull(1)
                }

                val genresArray = bestItem.optJSONArray("genres")
                val genres = mutableListOf<String>()
                if (genresArray != null) {
                    for (g in 0 until genresArray.length()) {
                        genres.add(genresArray.optString(g))
                    }
                }

                // If imdbId is present, we can provide high quality poster art from metahub proxy
                val posterUrl = imdbId?.let { "https://images.metahub.space/poster/medium/$it/img" }

                val metadata = ProgrammeMetadata(
                    source = MetadataSource.TRAKT,
                    title = title,
                    year = itemYear,
                    releaseDate = released,
                    rating = rating,
                    ratingCount = votes,
                    description = overview,
                    posterUrl = posterUrl,
                    genres = genres,
                    trailerUrl = trailerUrl,
                    trailerYoutubeId = ytId,
                    externalId = slug ?: imdbId,
                    webUrl = slug?.let { "https://trakt.tv/${if (bestType == "show") "shows" else "movies"}/$it" }
                )

                Result.success(metadata)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
}
