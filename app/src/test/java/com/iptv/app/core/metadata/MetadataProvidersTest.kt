package com.iptv.app.core.metadata

import com.iptv.app.core.metadata.providers.ImdbMetadataProvider
import com.iptv.app.core.metadata.providers.SratimMetadataProvider
import com.iptv.app.core.metadata.providers.TraktMetadataProvider
import com.iptv.app.core.metadata.providers.TvdbMetadataProvider
import com.iptv.app.data.NetworkClient
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.InputStream

class MockNetworkClient(
    private val handler: (url: String, method: String, body: String?) -> String
) : NetworkClient {
    override suspend fun openStream(url: String, headers: Map<String, String>): InputStream {
        val response = handler(url, "GET", null)
        return ByteArrayInputStream(response.toByteArray(Charsets.UTF_8))
    }

    override suspend fun postForm(url: String, formParams: Map<String, String>, headers: Map<String, String>): InputStream {
        val formStr = formParams.entries.joinToString("&") { "${it.key}=${it.value}" }
        val response = handler(url, "POST_FORM", formStr)
        return ByteArrayInputStream(response.toByteArray(Charsets.UTF_8))
    }

    override suspend fun postJson(url: String, jsonBody: String, headers: Map<String, String>): InputStream {
        val response = handler(url, "POST_JSON", jsonBody)
        return ByteArrayInputStream(response.toByteArray(Charsets.UTF_8))
    }
}

class MetadataProvidersTest {

    @Test
    fun `ImdbMetadataProvider parses suggestions and cinemeta metadata`() = runBlocking {
        val mockClient = MockNetworkClient { url, _, _ ->
            when {
                url.contains("sg.media-imdb.com") -> {
                    """{"d":[{"id":"tt0111161","l":"The Shawshank Redemption","y":1994,"i":{"imageUrl":"https://example.com/poster.jpg"}}]}"""
                }
                url.contains("v3-cinemeta.strem.io") -> {
                    """{
                        "meta": {
                            "name": "The Shawshank Redemption",
                            "year": "1994",
                            "imdbRating": "9.3",
                            "description": "Two imprisoned men bond over a number of years.",
                            "genres": ["Drama"],
                            "director": ["Frank Darabont"],
                            "cast": ["Tim Robbins", "Morgan Freeman"],
                            "poster": "https://example.com/poster.jpg",
                            "trailer": "https://www.youtube.com/watch?v=PLl99DlL6b4"
                        }
                    }"""
                }
                else -> "{}"
            }
        }

        val provider = ImdbMetadataProvider(mockClient)
        val result = provider.search("The Shawshank Redemption", null, "en")

        assertTrue(result.isSuccess)
        val meta = result.getOrNull()
        assertNotNull(meta)
        assertEquals("tt0111161", meta?.externalId)
        assertEquals("The Shawshank Redemption", meta?.title)
        assertEquals(1994, meta?.year)
        assertEquals(9.3, meta?.rating ?: 0.0, 0.01)
        assertEquals("https://www.youtube.com/watch?v=PLl99DlL6b4", meta?.trailerUrl)
        assertEquals(MetadataSource.IMDB, meta?.source)
    }

    @Test
    fun `TraktMetadataProvider parses movies and trailers`() = runBlocking {
        val mockClient = MockNetworkClient { url, _, _ ->
            if (url.contains("api.trakt.tv/search")) {
                """[
                    {
                        "type": "movie",
                        "score": 100,
                        "movie": {
                            "title": "Inception",
                            "year": 2010,
                            "ids": {
                                "trakt": 16,
                                "slug": "inception-2010",
                                "imdb": "tt1375666",
                                "tmdb": 27205
                            },
                            "overview": "A thief who steals corporate secrets through the use of dream-sharing technology.",
                            "rating": 8.66,
                            "votes": 35000,
                            "trailer": "https://youtube.com/watch?v=YoHD9XEInc0",
                            "genres": ["action", "sci-fi"]
                        }
                    }
                ]"""
            } else "{}"
        }

        val provider = TraktMetadataProvider(mockClient)
        val result = provider.search("Inception", 2010, "en")

        assertTrue(result.isSuccess)
        val meta = result.getOrNull()
        assertNotNull(meta)
        assertEquals("inception-2010", meta?.externalId)
        assertEquals("Inception", meta?.title)
        assertEquals(2010, meta?.year)
        assertEquals(8.7, meta?.rating?.let { Math.round(it * 10) / 10.0 } ?: 0.0, 0.05)
        assertEquals("https://youtube.com/watch?v=YoHD9XEInc0", meta?.trailerUrl)
        assertEquals(MetadataSource.TRAKT, meta?.source)
    }

    @Test
    fun `TvdbMetadataProvider handles auth token and search response`() = runBlocking {
        val mockClient = MockNetworkClient { url, _, _ ->
            when {
                url.contains("/login") -> """{"status":"success","data":{"token":"mock-tvdb-jwt-token"}}"""
                url.contains("/search") -> """{
                    "status": "success",
                    "data": [
                        {
                            "tvdb_id": "81189",
                            "name": "Breaking Bad",
                            "year": "2008",
                            "overview": "A high school chemistry teacher diagnosed with terminal lung cancer.",
                            "image_url": "https://thetvdb.com/banners/posters/81189-1.jpg",
                            "score": 9.5,
                            "primary_type": "series"
                        }
                    ]
                }"""
                else -> "{}"
            }
        }

        val provider = TvdbMetadataProvider(mockClient)
        val result = provider.search("Breaking Bad", 2008, "en")

        assertTrue(result.isSuccess)
        val meta = result.getOrNull()
        assertNotNull(meta)
        assertEquals("81189", meta?.externalId)
        assertEquals("Breaking Bad", meta?.title)
        assertEquals(2008, meta?.year)
        assertEquals(9.5, meta?.rating ?: 0.0, 0.01)
        assertEquals(MetadataSource.TVDB, meta?.source)
    }

    @Test
    fun `SratimMetadataProvider parses HTML search and JSON-LD schema`() = runBlocking {
        val mockClient = MockNetworkClient { url, method, _ ->
            if (method == "POST_FORM" && url.contains("sratim.co.il/search.php")) {
                """
                <html>
                    <body>
                        <a href="/tt12345/movie-title">פאודה עונה 1</a>
                    </body>
                </html>
                """
            } else if (url.contains("sratim.co.il/tt12345/")) {
                """
                <html>
                    <head>
                        <script type="application/ld+json">
                        {
                            "@context": "https://schema.org",
                            "@type": "Movie",
                            "name": "פאודה",
                            "description": "יחידת מסתערבים פועלת ללכידת מחבל בכיר.",
                            "datePublished": "2015-02-15",
                            "image": "https://www.sratim.co.il/images/movies/12345.jpg",
                            "aggregateRating": {
                                "@type": "AggregateRating",
                                "ratingValue": "8.8"
                            }
                        }
                        </script>
                    </head>
                    <body></body>
                </html>
                """
            } else "{}"
        }

        val provider = SratimMetadataProvider(mockClient)
        val result = provider.search("פאודה", null, "he")

        assertTrue(result.isSuccess)
        val meta = result.getOrNull()
        assertNotNull(meta)
        assertEquals("tt12345", meta?.externalId)
        assertEquals("פאודה", meta?.title)
        assertEquals(2015, meta?.year)
        assertEquals(8.8, meta?.rating ?: 0.0, 0.01)
        assertEquals("he", meta?.language)
        assertEquals(MetadataSource.SRATIM, meta?.source)
    }
}
