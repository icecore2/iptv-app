package com.iptv.app.core.metadata.providers

import com.iptv.app.core.metadata.MetadataSource
import com.iptv.app.core.metadata.ProgrammeMetadata
import com.iptv.app.data.NetworkClient
import com.iptv.app.data.OkHttpNetworkClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URLEncoder

class TvdbMetadataProvider(
    private val networkClient: NetworkClient = OkHttpNetworkClient(),
    private val tvdbApiKeyProvider: () -> String = { DEFAULT_API_KEY }
) : MetadataProvider {

    override val source: MetadataSource = MetadataSource.TVDB

    companion object {
        // TVDB Project API Key
        const val DEFAULT_API_KEY = "d9ba7d75-ec75-470a-8bfd-465406085a69"
        private const val BASE_URL = "https://api4.thetvdb.com/v4"
    }

    private var cachedToken: String? = null
    private var tokenExpiryEpoch: Long = 0
    private val authMutex = Mutex()

    private suspend fun getAuthToken(apiKey: String): String? = authMutex.withLock {
        val now = System.currentTimeMillis()
        if (cachedToken != null && now < tokenExpiryEpoch) {
            return cachedToken
        }

        return try {
            val loginBody = JSONObject().apply {
                put("apikey", apiKey)
            }.toString()

            val response = networkClient.postJson(
                url = "$BASE_URL/login",
                jsonBody = loginBody
            ).bufferedReader(Charsets.UTF_8).use { it.readText() }

            val json = JSONObject(response)
            val token = json.optJSONObject("data")?.optString("token")
            if (!token.isNullOrBlank()) {
                cachedToken = token
                tokenExpiryEpoch = now + 82800000L // 23 hours
                token
            } else {
                null
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun mapLanguageCode(lang: String): String {
        return when (lang.lowercase()) {
            "he", "heb", "hebrew" -> "heb"
            "es", "spa", "spanish" -> "spa"
            "fr", "fra", "french" -> "fra"
            "de", "deu", "german" -> "deu"
            "ru", "rus", "russian" -> "rus"
            "it", "ita", "italian" -> "ita"
            "pt", "por", "portuguese" -> "por"
            "ar", "ara", "arabic" -> "ara"
            else -> "eng"
        }
    }

    override suspend fun search(query: String, year: Int?, language: String): Result<ProgrammeMetadata?> =
        withContext(Dispatchers.IO) {
            try {
                val cleanQuery = query.trim()
                if (cleanQuery.isBlank()) return@withContext Result.success(null)

                val apiKey = tvdbApiKeyProvider().trim().ifBlank { DEFAULT_API_KEY }
                if (apiKey.isBlank()) return@withContext Result.success(null)

                val token = getAuthToken(apiKey) ?: return@withContext Result.success(null)
                val encodedQuery = URLEncoder.encode(cleanQuery, "UTF-8")
                val langCode = mapLanguageCode(language)

                val searchUrl = "$BASE_URL/search?query=$encodedQuery&language=$langCode"
                val response = networkClient.openStream(
                    url = searchUrl,
                    headers = mapOf(
                        "Authorization" to "Bearer $token",
                        "Accept" to "application/json"
                    )
                ).bufferedReader(Charsets.UTF_8).use { it.readText() }

                val rootJson = JSONObject(response)
                val dataArray = rootJson.optJSONArray("data") ?: return@withContext Result.success(null)
                if (dataArray.length() == 0) return@withContext Result.success(null)

                var bestItem: JSONObject? = null
                for (i in 0 until dataArray.length()) {
                    val item = dataArray.optJSONObject(i) ?: continue
                    if (year != null) {
                        val itemYear = item.optString("year").toIntOrNull()
                        if (itemYear != null && kotlin.math.abs(itemYear - year) <= 1) {
                            bestItem = item
                            break
                        }
                    } else {
                        bestItem = item
                        break
                    }
                }

                if (bestItem == null) {
                    bestItem = dataArray.optJSONObject(0)
                }

                if (bestItem == null) return@withContext Result.success(null)

                val name = bestItem.optString("name", query)
                val itemYear = bestItem.optString("year").toIntOrNull() ?: year
                val tvdbId = bestItem.optString("tvdb_id")
                val imageUrl = bestItem.optString("image_url").takeIf { it.isNotBlank() }
                val score = bestItem.optDouble("score", 0.0).takeIf { it > 0.0 }

                // Check localized overview in translations/overviews
                var overview: String? = null
                val overviewsObj = bestItem.optJSONObject("overviews")
                if (overviewsObj != null) {
                    overview = overviewsObj.optString(langCode).takeIf { it.isNotBlank() }
                        ?: overviewsObj.optString("eng").takeIf { it.isNotBlank() }
                }
                if (overview.isNullOrBlank()) {
                    overview = bestItem.optString("overview").takeIf { it.isNotBlank() }
                }

                val genresArray = bestItem.optJSONArray("genres")
                val genres = mutableListOf<String>()
                if (genresArray != null) {
                    for (g in 0 until genresArray.length()) {
                        genres.add(genresArray.optString(g))
                    }
                }

                val metadata = ProgrammeMetadata(
                    source = MetadataSource.TVDB,
                    title = name,
                    year = itemYear,
                    rating = score,
                    description = overview,
                    language = language,
                    posterUrl = imageUrl,
                    genres = genres,
                    externalId = tvdbId,
                    webUrl = if (tvdbId.isNotBlank()) "https://thetvdb.com/dereferrer/series/$tvdbId" else null
                )

                Result.success(metadata)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
}
