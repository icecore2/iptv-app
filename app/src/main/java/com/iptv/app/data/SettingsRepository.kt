package com.iptv.app.data

import android.content.Context
import android.content.SharedPreferences
import com.iptv.app.core.model.AppSettings
import com.iptv.app.ui.viewmodel.AspectRatioMode
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

interface SettingsRepository {
    val settingsFlow: StateFlow<AppSettings>
    suspend fun getSettings(): AppSettings
    suspend fun updateSettings(transform: (AppSettings) -> AppSettings)
    suspend fun resetToDefaults()
}

class SharedPreferencesSettingsRepository(
    context: Context,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : SettingsRepository {

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val _settingsFlow = MutableStateFlow(loadFromPrefs())
    override val settingsFlow: StateFlow<AppSettings> = _settingsFlow.asStateFlow()

    companion object {
        private const val PREFS_NAME = "iptv_app_settings_prefs"

        private const val KEY_SHOW_CHANNEL_LOGOS = "pref_show_channel_logos"
        private const val KEY_ENABLE_PAGINATION = "pref_enable_pagination"
        private const val KEY_PAGE_SIZE = "pref_page_size"
        private const val KEY_SHOW_EPG_IN_LIST = "pref_show_epg_in_list"
        private const val KEY_AUTO_LOAD_LAST_PLAYLIST = "pref_auto_load_last_playlist"

        private const val KEY_BUFFER_DURATION = "pref_buffer_duration_seconds"
        private const val KEY_BUFFER_STORAGE_LIMIT = "pref_buffer_storage_limit_mb"
        private const val KEY_KEEP_SCREEN_ON = "pref_keep_screen_on"
        private const val KEY_FAST_SWITCHING = "pref_fast_channel_switching"
        private const val KEY_HARDWARE_ACCELERATION = "pref_hardware_acceleration"
        private const val KEY_DEFAULT_ASPECT_RATIO = "pref_default_aspect_ratio"
        private const val KEY_SHOW_STREAM_INFO_OVERLAY = "pref_show_stream_info_overlay"
    }

    private fun loadFromPrefs(): AppSettings {
        val defaultMode = AspectRatioMode.FIT
        val modeStr = prefs.getString(KEY_DEFAULT_ASPECT_RATIO, defaultMode.name) ?: defaultMode.name
        val parsedMode = try {
            AspectRatioMode.valueOf(modeStr)
        } catch (_: Exception) {
            defaultMode
        }

        return AppSettings(
            showChannelLogos = prefs.getBoolean(KEY_SHOW_CHANNEL_LOGOS, true),
            enablePagination = prefs.getBoolean(KEY_ENABLE_PAGINATION, true),
            pageSize = prefs.getInt(KEY_PAGE_SIZE, 50),
            showEpgInList = prefs.getBoolean(KEY_SHOW_EPG_IN_LIST, true),
            autoLoadLastPlaylist = prefs.getBoolean(KEY_AUTO_LOAD_LAST_PLAYLIST, false),
            bufferDurationSeconds = prefs.getInt(KEY_BUFFER_DURATION, 15),
            bufferStorageLimitMb = prefs.getInt(KEY_BUFFER_STORAGE_LIMIT, 1024),
            keepScreenOn = prefs.getBoolean(KEY_KEEP_SCREEN_ON, true),
            fastChannelSwitching = prefs.getBoolean(KEY_FAST_SWITCHING, true),
            hardwareAcceleration = prefs.getBoolean(KEY_HARDWARE_ACCELERATION, true),
            defaultAspectRatio = parsedMode,
            showStreamInfoOverlay = prefs.getBoolean(KEY_SHOW_STREAM_INFO_OVERLAY, false)
        )
    }

    override suspend fun getSettings(): AppSettings = withContext(ioDispatcher) {
        _settingsFlow.value
    }

    override suspend fun updateSettings(transform: (AppSettings) -> AppSettings): Unit = withContext(ioDispatcher) {
        val current = _settingsFlow.value
        val updated = transform(current)
        prefs.edit().apply {
            putBoolean(KEY_SHOW_CHANNEL_LOGOS, updated.showChannelLogos)
            putBoolean(KEY_ENABLE_PAGINATION, updated.enablePagination)
            putInt(KEY_PAGE_SIZE, updated.pageSize)
            putBoolean(KEY_SHOW_EPG_IN_LIST, updated.showEpgInList)
            putBoolean(KEY_AUTO_LOAD_LAST_PLAYLIST, updated.autoLoadLastPlaylist)
            putInt(KEY_BUFFER_DURATION, updated.bufferDurationSeconds)
            putInt(KEY_BUFFER_STORAGE_LIMIT, updated.bufferStorageLimitMb)
            putBoolean(KEY_KEEP_SCREEN_ON, updated.keepScreenOn)
            putBoolean(KEY_FAST_SWITCHING, updated.fastChannelSwitching)
            putBoolean(KEY_HARDWARE_ACCELERATION, updated.hardwareAcceleration)
            putString(KEY_DEFAULT_ASPECT_RATIO, updated.defaultAspectRatio.name)
            putBoolean(KEY_SHOW_STREAM_INFO_OVERLAY, updated.showStreamInfoOverlay)
        }.apply()
        _settingsFlow.value = updated
    }

    override suspend fun resetToDefaults(): Unit = withContext(ioDispatcher) {
        prefs.edit().clear().apply()
        val defaults = AppSettings()
        _settingsFlow.value = defaults
    }
}

class InMemorySettingsRepository(
    initialSettings: AppSettings = AppSettings()
) : SettingsRepository {

    private val _settingsFlow = MutableStateFlow(initialSettings)
    override val settingsFlow: StateFlow<AppSettings> = _settingsFlow.asStateFlow()

    override suspend fun getSettings(): AppSettings = _settingsFlow.value

    override suspend fun updateSettings(transform: (AppSettings) -> AppSettings) {
        val updated = transform(_settingsFlow.value)
        _settingsFlow.value = updated
    }

    override suspend fun resetToDefaults() {
        _settingsFlow.value = AppSettings()
    }
}
