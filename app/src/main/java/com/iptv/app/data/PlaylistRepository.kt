package com.iptv.app.data

import com.iptv.app.core.model.M3uPlaylist
import com.iptv.app.core.parser.M3uParser
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class PlaylistRepository(
    private val networkClient: NetworkClient = OkHttpNetworkClient(),
    private val parser: M3uParser = M3uParser(),
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {

    suspend fun loadPlaylistFromUrl(url: String, headers: Map<String, String> = emptyMap()): Result<M3uPlaylist> =
        withContext(ioDispatcher) {
            try {
                val stream = networkClient.openStream(url, headers)
                val playlist = parser.parse(stream)
                Result.success(playlist)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    fun loadPlaylistFromString(content: String): M3uPlaylist {
        return parser.parse(content)
    }
}
