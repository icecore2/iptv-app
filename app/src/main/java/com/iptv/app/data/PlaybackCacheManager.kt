package com.iptv.app.data

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

/**
 * Manages ExoPlayer's disk cache, time-shift replay buffer, and storage quota.
 */
@OptIn(UnstableApi::class)
object PlaybackCacheManager {

    private const val CACHE_DIR_NAME = "iptv_stream_buffer"
    private var simpleCache: SimpleCache? = null
    private var databaseProvider: StandaloneDatabaseProvider? = null
    private var currentLimitMb: Int = 1024

    private val _storageUsedBytes = MutableStateFlow(0L)
    val storageUsedBytes: StateFlow<Long> = _storageUsedBytes.asStateFlow()

    private val scope = CoroutineScope(Dispatchers.IO)

    @Synchronized
    fun getCache(context: Context, limitMb: Int = currentLimitMb): SimpleCache {
        val appContext = context.applicationContext
        val cacheDir = File(appContext.cacheDir, CACHE_DIR_NAME)
        if (!cacheDir.exists()) {
            cacheDir.mkdirs()
        }

        if (simpleCache == null || currentLimitMb != limitMb) {
            currentLimitMb = limitMb
            val maxBytes = limitMb * 1024L * 1024L
            val evictor = LeastRecentlyUsedCacheEvictor(maxBytes)

            if (databaseProvider == null) {
                databaseProvider = StandaloneDatabaseProvider(appContext)
            }

            // If an existing cache was created with a different limit, release it first
            simpleCache?.release()
            simpleCache = SimpleCache(cacheDir, evictor, databaseProvider!!)
            refreshStorageUsage(context)
        }

        return simpleCache!!
    }

    /**
     * Builds a CacheDataSource factory using the managed SimpleCache and standard HTTP upstream.
     */
    @Synchronized
    fun createDataSourceFactory(context: Context, limitMb: Int = currentLimitMb): DataSource.Factory {
        val cache = getCache(context, limitMb)
        val upstreamFactory = DefaultHttpDataSource.Factory()
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(15_000)
            .setReadTimeoutMs(15_000)

        return CacheDataSource.Factory()
            .setCache(cache)
            .setUpstreamDataSourceFactory(upstreamFactory)
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
    }

    /**
     * Queries and refreshes the bytes currently occupied by the buffer cache.
     */
    fun refreshStorageUsage(context: Context) {
        scope.launch {
            val used = getUsedStorageBytes(context)
            _storageUsedBytes.value = used
        }
    }

    /**
     * Returns the exact number of bytes currently used by the media cache.
     */
    @Synchronized
    fun getUsedStorageBytes(context: Context): Long {
        return try {
            simpleCache?.cacheSpace ?: calculateDirSize(File(context.applicationContext.cacheDir, CACHE_DIR_NAME))
        } catch (_: Exception) {
            0L
        }
    }

    /**
     * Returns the total free storage space available on the device cache filesystem.
     */
    fun getFreeDiskSpaceBytes(context: Context): Long {
        return try {
            context.applicationContext.cacheDir.freeSpace
        } catch (_: Exception) {
            0L
        }
    }

    /**
     * Clears all cached media segments and resets the storage meter to zero.
     */
    @Synchronized
    fun clearCache(context: Context) {
        try {
            val cache = simpleCache
            if (cache != null) {
                val keys = cache.keys.toList()
                for (key in keys) {
                    try {
                        cache.removeResource(key)
                    } catch (_: Exception) {}
                }
            } else {
                val cacheDir = File(context.applicationContext.cacheDir, CACHE_DIR_NAME)
                if (cacheDir.exists()) {
                    cacheDir.deleteRecursively()
                }
            }
        } catch (_: Exception) {}
        _storageUsedBytes.value = 0L
        refreshStorageUsage(context)
    }

    private fun calculateDirSize(dir: File): Long {
        if (!dir.exists()) return 0L
        var size = 0L
        dir.listFiles()?.forEach { file ->
            size += if (file.isDirectory) calculateDirSize(file) else file.length()
        }
        return size
    }
}
