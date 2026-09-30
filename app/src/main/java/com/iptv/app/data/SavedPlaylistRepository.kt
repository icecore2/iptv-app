package com.iptv.app.data

import android.content.Context
import android.content.SharedPreferences
import com.iptv.app.core.model.SavedPlaylistPair
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

interface SavedPlaylistRepository {
    suspend fun getSavedPlaylists(): List<SavedPlaylistPair>
    suspend fun savePlaylist(pair: SavedPlaylistPair): SavedPlaylistPair
    suspend fun updatePlaylist(pair: SavedPlaylistPair)
    suspend fun deletePlaylist(id: String)
    suspend fun getActivePairId(): String?
    suspend fun setActivePairId(id: String?)
    suspend fun updateFavorites(pairId: String, favoriteIds: Set<String>)
}

val DEFAULT_SAMPLE_PAIR = SavedPlaylistPair(
    id = "sample_demo_profile",
    name = "Demo Channels & EPG",
    playlistUrl = SampleDataProvider.DEFAULT_SAMPLE_PLAYLIST_URL,
    epgUrl = SampleDataProvider.DEFAULT_SAMPLE_EPG_URL,
    isSample = true
)

class SharedPreferencesSavedPlaylistRepository(
    context: Context,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : SavedPlaylistRepository {

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    companion object {
        private const val PREFS_NAME = "iptv_saved_playlists_prefs"
        private const val KEY_PLAYLIST_LIST = "saved_playlists_json"
        private const val KEY_ACTIVE_PAIR_ID = "active_playlist_pair_id"
        private const val KEY_IS_INITIALIZED = "has_initialized_defaults"
    }

    override suspend fun getSavedPlaylists(): List<SavedPlaylistPair> = withContext(ioDispatcher) {
        val hasInit = prefs.getBoolean(KEY_IS_INITIALIZED, false)
        val jsonString = prefs.getString(KEY_PLAYLIST_LIST, null)

        if (!hasInit && jsonString.isNullOrBlank()) {
            val initial = listOf(DEFAULT_SAMPLE_PAIR)
            saveListToPrefs(initial)
            prefs.edit().putBoolean(KEY_IS_INITIALIZED, true).apply()
            return@withContext initial
        }

        if (jsonString.isNullOrBlank()) {
            return@withContext emptyList()
        }

        parseJsonList(jsonString)
    }

    override suspend fun savePlaylist(pair: SavedPlaylistPair): SavedPlaylistPair = withContext(ioDispatcher) {
        val current = getSavedPlaylists().toMutableList()
        val index = current.indexOfFirst { it.id == pair.id }
        if (index >= 0) {
            current[index] = pair
        } else {
            current.add(pair)
        }
        saveListToPrefs(current)
        pair
    }

    override suspend fun updatePlaylist(pair: SavedPlaylistPair): Unit = withContext(ioDispatcher) {
        val current = getSavedPlaylists().toMutableList()
        val index = current.indexOfFirst { it.id == pair.id }
        if (index >= 0) {
            current[index] = pair
            saveListToPrefs(current)
        }
    }

    override suspend fun deletePlaylist(id: String): Unit = withContext(ioDispatcher) {
        val current = getSavedPlaylists().filterNot { it.id == id }
        saveListToPrefs(current)

        if (getActivePairId() == id) {
            setActivePairId(null)
        }
    }

    override suspend fun getActivePairId(): String? = withContext(ioDispatcher) {
        prefs.getString(KEY_ACTIVE_PAIR_ID, null)
    }

    override suspend fun setActivePairId(id: String?): Unit = withContext(ioDispatcher) {
        if (id == null) {
            prefs.edit().remove(KEY_ACTIVE_PAIR_ID).apply()
        } else {
            prefs.edit().putString(KEY_ACTIVE_PAIR_ID, id).apply()
        }
    }

    override suspend fun updateFavorites(pairId: String, favoriteIds: Set<String>): Unit = withContext(ioDispatcher) {
        val current = getSavedPlaylists().toMutableList()
        val index = current.indexOfFirst { it.id == pairId }
        if (index >= 0) {
            val updated = current[index].copy(favoriteIds = favoriteIds)
            current[index] = updated
            saveListToPrefs(current)
        }
    }

    private fun saveListToPrefs(list: List<SavedPlaylistPair>) {
        val array = JSONArray()
        for (item in list) {
            val obj = JSONObject().apply {
                put("id", item.id)
                put("name", item.name)
                put("playlistUrl", item.playlistUrl)
                if (item.epgUrl != null) put("epgUrl", item.epgUrl)
                put("isSample", item.isSample)
                put("createdAt", item.createdAt)
                val favArr = JSONArray()
                item.favoriteIds.forEach { favArr.put(it) }
                put("favoriteIds", favArr)
            }
            array.put(obj)
        }
        prefs.edit().putString(KEY_PLAYLIST_LIST, array.toString()).apply()
    }

    private fun parseJsonList(jsonString: String): List<SavedPlaylistPair> {
        val result = mutableListOf<SavedPlaylistPair>()
        try {
            val array = JSONArray(jsonString)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val favSet = mutableSetOf<String>()
                if (obj.has("favoriteIds")) {
                    val favArr = obj.getJSONArray("favoriteIds")
                    for (j in 0 until favArr.length()) {
                        favSet.add(favArr.getString(j))
                    }
                }
                result.add(
                    SavedPlaylistPair(
                        id = obj.getString("id"),
                        name = obj.getString("name"),
                        playlistUrl = obj.getString("playlistUrl"),
                        epgUrl = if (obj.has("epgUrl") && !obj.isNull("epgUrl")) obj.getString("epgUrl") else null,
                        isSample = obj.optBoolean("isSample", false),
                        createdAt = obj.optLong("createdAt", System.currentTimeMillis()),
                        favoriteIds = favSet
                    )
                )
            }
        } catch (_: Exception) {
            // Return empty or partial list on corrupted JSON
        }
        return result
    }
}

/**
 * In-memory implementation of SavedPlaylistRepository for unit testing and test environments.
 */
class InMemorySavedPlaylistRepository(
    initialList: List<SavedPlaylistPair> = listOf(DEFAULT_SAMPLE_PAIR),
    private var activeId: String? = DEFAULT_SAMPLE_PAIR.id
) : SavedPlaylistRepository {

    private val playlists = initialList.toMutableList()

    override suspend fun getSavedPlaylists(): List<SavedPlaylistPair> {
        return playlists.toList()
    }

    override suspend fun savePlaylist(pair: SavedPlaylistPair): SavedPlaylistPair {
        val index = playlists.indexOfFirst { it.id == pair.id }
        if (index >= 0) {
            playlists[index] = pair
        } else {
            playlists.add(pair)
        }
        return pair
    }

    override suspend fun updatePlaylist(pair: SavedPlaylistPair) {
        val index = playlists.indexOfFirst { it.id == pair.id }
        if (index >= 0) {
            playlists[index] = pair
        }
    }

    override suspend fun deletePlaylist(id: String) {
        playlists.removeAll { it.id == id }
        if (activeId == id) {
            activeId = null
        }
    }

    override suspend fun getActivePairId(): String? {
        return activeId
    }

    override suspend fun setActivePairId(id: String?) {
        activeId = id
    }

    override suspend fun updateFavorites(pairId: String, favoriteIds: Set<String>) {
        val index = playlists.indexOfFirst { it.id == pairId }
        if (index >= 0) {
            playlists[index] = playlists[index].copy(favoriteIds = favoriteIds)
        }
    }
}
