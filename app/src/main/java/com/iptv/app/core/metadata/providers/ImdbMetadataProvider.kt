package com.iptv.app.core.metadata.providers

import com.iptv.app.core.metadata.MetadataSource
import com.iptv.app.core.metadata.ProgrammeMetadata
import com.iptv.app.data.NetworkClient
import com.iptv.app.data.OkHttpNetworkClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URLEncoder

class ImdbMetadataProvider(
    private val networkClient: NetworkClient = OkHttpNetworkClient()
) : MetadataProvider {

    override val source: MetadataSource = MetadataSource.IMDB

    override suspend fun search(query: String, year: Int?, language: String): Result<ProgrammeMetadata?> =
        withContext(Dispatchers.IO) {
            try {
                val cleanQuery = query.trim().lowercase()
                if (cleanQuery.isBlank()) return@withContext Result.success(null)

                val encodedQuery = URLEncoder.encode(cleanQuery, "UTF-8")
                val searchUrl = "https://v3.sg.media-imdb.com/suggestion/x/$encodedQuery.json"
                val searchResponse = networkClient.openStream(searchUrl).bufferedReader(Charsets.UTF_8).use { it.readText() }

                val searchJson = JSONObject(searchResponse)
                val dArray = searchJson.optJSONArray("d") ?: return@withContext Result.success(null)
                if (dArray.length() == 0) return@withContext Result.success(null)

                // Find best matching item (considering year if provided)
                var bestItem: JSONObject? = null
                for (i in 0 until dArray.length()) {
                    val item = dArray.optJSONObject(i) ?: continue
                    val id = item.optString("id")
                    if (!id.startsWith("tt")) continue

                    if (year != null) {
                        val itemYear = item.optInt("y", 0)
                        if (itemYear != 0 && kotlin.math.abs(itemYear - year) <= 1) {
                            bestItem = item
                            break
                        }
                    } else {
                        bestItem = item
                        break
                    }
                }

                if (bestItem == null) {
                    bestItem = dArray.optJSONObject(0)
                }

                if (bestItem == null) return@withContext Result.success(null)

                val imdbId = bestItem.optString("id")
                val title = bestItem.optString("l", query)
                val itemYear = bestItem.optInt("y").takeIf { it > 0 } ?: year
                val posterUrl = bestItem.optJSONObject("i")?.optString("imageUrl")
                val stars = bestItem.optString("s")
                val qid = bestItem.optString("qid", "movie")
                val isSeries = qid.contains("tv", ignoreCase = true) || qid.contains("series", ignoreCase = true)
                val cinemetaType = if (isSeries) "series" else "movie"

                // Enrich via Cinemeta Open IMDb proxy for description, rating, and trailer
                var enrichedRating: Double? = null
                var enrichedDesc: String? = null
                var enrichedReleaseDate: String? = null
                var enrichedPoster: String? = posterUrl
                var enrichedGenres = emptyList<String>()
                var enrichedDirector: String? = null
                var enrichedCast = if (stars.isNotBlank()) stars.split(",").map { it.trim() } else emptyList()
                var enrichedTrailerId: String? = null
                var enrichedTrailerUrl: String? = null

                if (imdbId.startsWith("tt")) {
                    try {
                        val cinemetaUrl = "https://v3-cinemeta.strem.io/meta/$cinemetaType/$imdbId.json"
                        val metaResponse = networkClient.openStream(cinemetaUrl).bufferedReader(Charsets.UTF_8).use { it.readText() }
                        val metaJson = JSONObject(metaResponse).optJSONObject("meta")
                        if (metaJson != null) {
                            enrichedRating = metaJson.optString("imdbRating").toDoubleOrNull()
                            enrichedDesc = metaJson.optString("description").takeIf { it.isNotBlank() }
                            enrichedReleaseDate = metaJson.optString("released").takeIf { it.isNotBlank() }
                            enrichedPoster = metaJson.optString("poster").takeIf { it.isNotBlank() } ?: enrichedPoster

                            val genresArray = metaJson.optJSONArray("genres") ?: metaJson.optJSONArray("genre")
                            if (genresArray != null) {
                                val list = mutableListOf<String>()
                                for (g in 0 until genresArray.length()) {
                                    val genre = genresArray.optString(g)
                                    if (genre.isNotBlank()) list.add(genre)
                                }
                                enrichedGenres = list
                            }

                            val directorArray = metaJson.optJSONArray("director")
                            if (directorArray != null && directorArray.length() > 0) {
                                enrichedDirector = directorArray.optString(0)
                            }

                            val castArray = metaJson.optJSONArray("cast")
                            if (castArray != null && castArray.length() > 0) {
                                val list = mutableListOf<String>()
                                for (c in 0 until castArray.length()) {
                                    val actor = castArray.optString(c)
                                    if (actor.isNotBlank()) list.add(actor)
                                }
                                enrichedCast = list
                            }

                            val trailersArray = metaJson.optJSONArray("trailers")
                            if (trailersArray != null && trailersArray.length() > 0) {
                                for (t in 0 until trailersArray.length()) {
                                    val trObj = trailersArray.optJSONObject(t) ?: continue
                                    val sourceId = trObj.optString("source")
                                    if (sourceId.isNotBlank()) {
                                        enrichedTrailerId = sourceId
                                        break
                                    }
                                }
                            }

                            if (enrichedTrailerId.isNullOrBlank()) {
                                val trailerStreams = metaJson.optJSONArray("trailerStreams")
                                if (trailerStreams != null && trailerStreams.length() > 0) {
                                    enrichedTrailerId = trailerStreams.optJSONObject(0)?.optString("ytId")
                                }
                            }

                            if (enrichedTrailerId.isNullOrBlank()) {
                                val trStr = metaJson.optString("trailer")
                                if (trStr.isNotBlank()) {
                                    enrichedTrailerUrl = trStr
                                }
                            }
                        }
                    } catch (_: Exception) {
                        // Keep initial suggestion data on Cinemeta failure
                    }
                }

                val metadata = ProgrammeMetadata(
                    source = MetadataSource.IMDB,
                    title = title,
                    year = itemYear,
                    releaseDate = enrichedReleaseDate,
                    rating = enrichedRating,
                    description = enrichedDesc,
                    posterUrl = enrichedPoster,
                    genres = enrichedGenres,
                    director = enrichedDirector,
                    cast = enrichedCast,
                    trailerYoutubeId = enrichedTrailerId,
                    trailerUrl = enrichedTrailerUrl ?: enrichedTrailerId?.let { "https://www.youtube.com/watch?v=$it" },
                    externalId = imdbId,
                    webUrl = if (imdbId.isNotBlank()) "https://www.imdb.com/title/$imdbId" else null
                )

                Result.success(metadata)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
}
