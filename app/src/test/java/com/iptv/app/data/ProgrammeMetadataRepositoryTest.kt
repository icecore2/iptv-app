package com.iptv.app.data

import com.iptv.app.core.metadata.MetadataSource
import com.iptv.app.core.metadata.MockNetworkClient
import com.iptv.app.core.metadata.providers.ImdbMetadataProvider
import com.iptv.app.core.metadata.providers.TraktMetadataProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class ProgrammeMetadataRepositoryTest {

    @Test
    fun `repository returns cached result on subsequent calls without secondary network request`() = runBlocking {
        var networkHitCount = 0
        val mockClient = MockNetworkClient { url, _, _ ->
            networkHitCount++
            when {
                url.contains("sg.media-imdb.com") -> {
                    """{"d":[{"id":"tt0111161","l":"The Shawshank Redemption","y":1994,"i":{"imageUrl":"https://example.com/poster.jpg"}}]}"""
                }
                url.contains("v3-cinemeta.strem.io") -> {
                    """{"meta":{"name":"The Shawshank Redemption","year":"1994","imdbRating":"9.3"}}"""
                }
                else -> "{}"
            }
        }

        val imdbProvider = ImdbMetadataProvider(mockClient)
        val repository = ProgrammeMetadataRepository(imdbProvider = imdbProvider)

        val result1 = repository.resolveMetadata("The Shawshank Redemption [1080p]", preferredSource = MetadataSource.IMDB)
        assertTrue(result1.isSuccess)
        assertNotNull(result1.getOrNull())
        val hitsAfterFirst = networkHitCount
        assertTrue(hitsAfterFirst > 0)

        // Second call with same title should hit LRU cache and not make further network calls
        val result2 = repository.resolveMetadata("The Shawshank Redemption [1080p]", preferredSource = MetadataSource.IMDB)
        assertTrue(result2.isSuccess)
        assertNotNull(result2.getOrNull())
        assertEquals(hitsAfterFirst, networkHitCount)
        assertEquals(result1.getOrNull()?.title, result2.getOrNull()?.title)
    }

    @Test
    fun `repository falls back to alternate provider if preferred provider returns no results`() = runBlocking {
        val mockClient = MockNetworkClient { url, _, _ ->
            when {
                url.contains("api.trakt.tv") -> "[]"
                url.contains("sg.media-imdb.com") -> {
                    """{"d":[{"id":"tt1375666","l":"Inception","y":2010,"i":{"imageUrl":"https://example.com/poster.jpg"}}]}"""
                }
                url.contains("v3-cinemeta.strem.io") -> {
                    """{"meta":{"name":"Inception","year":"2010","imdbRating":"8.8"}}"""
                }
                else -> "{}"
            }
        }

        val traktProvider = TraktMetadataProvider(mockClient)
        val imdbProvider = ImdbMetadataProvider(mockClient)
        val repository = ProgrammeMetadataRepository(
            imdbProvider = imdbProvider,
            traktProvider = traktProvider
        )

        val result = repository.resolveMetadata("Inception (2010)", preferredSource = MetadataSource.TRAKT)
        assertTrue(result.isSuccess)
        val meta = result.getOrNull()
        assertNotNull(meta)
        assertEquals(MetadataSource.IMDB, meta?.source)
        assertEquals("Inception", meta?.title)
        assertEquals(2010, meta?.year)
    }

    @Test
    fun `resolveAllSources queries providers for tabbed split window`() = runBlocking {
        val mockClient = MockNetworkClient { url, _, _ ->
            when {
                url.contains("sg.media-imdb.com") -> {
                    """{"d":[{"id":"tt0172495","l":"Gladiator","y":2000,"i":{"imageUrl":"https://example.com/poster.jpg"}}]}"""
                }
                url.contains("v3-cinemeta.strem.io") -> {
                    """{"meta":{"name":"Gladiator","year":"2000","imdbRating":"8.5"}}"""
                }
                url.contains("api.trakt.tv") -> {
                    """[{"type":"movie","movie":{"title":"Gladiator","year":2000,"ids":{"trakt":99},"rating":8.4}}]"""
                }
                else -> "{}"
            }
        }

        val repository = ProgrammeMetadataRepository(
            imdbProvider = ImdbMetadataProvider(mockClient),
            traktProvider = TraktMetadataProvider(mockClient)
        )

        val allResults = repository.resolveAllSources("Gladiator [2000]")
        assertTrue(allResults.containsKey(MetadataSource.IMDB))
        assertTrue(allResults.containsKey(MetadataSource.TRAKT))
    }
}
