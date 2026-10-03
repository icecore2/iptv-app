package com.iptv.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.iptv.app.core.model.AppSettings
import com.iptv.app.data.InMemorySettingsRepository
import com.iptv.app.data.SettingsRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val settingsRepository: SettingsRepository = InMemorySettingsRepository()
) : ViewModel() {

    val settings: StateFlow<AppSettings> = settingsRepository.settingsFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = AppSettings()
        )

    fun toggleChannelLogos(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.updateSettings { it.copy(showChannelLogos = enabled) }
        }
    }

    fun togglePagination(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.updateSettings { it.copy(enablePagination = enabled) }
        }
    }

    fun setPageSize(size: Int) {
        viewModelScope.launch {
            settingsRepository.updateSettings { it.copy(pageSize = size) }
        }
    }

    fun toggleShowEpgInList(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.updateSettings { it.copy(showEpgInList = enabled) }
        }
    }

    fun toggleAutoLoadLastPlaylist(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.updateSettings { it.copy(autoLoadLastPlaylist = enabled) }
        }
    }

    fun setBufferDuration(seconds: Int) {
        viewModelScope.launch {
            settingsRepository.updateSettings { it.copy(bufferDurationSeconds = seconds) }
        }
    }

    fun setBufferStorageLimit(limitMb: Int) {
        viewModelScope.launch {
            settingsRepository.updateSettings { it.copy(bufferStorageLimitMb = limitMb) }
        }
    }

    fun clearBufferCache(context: android.content.Context) {
        viewModelScope.launch {
            com.iptv.app.data.PlaybackCacheManager.clearCache(context)
        }
    }

    fun refreshBufferStorage(context: android.content.Context) {
        com.iptv.app.data.PlaybackCacheManager.refreshStorageUsage(context)
    }

    fun toggleKeepScreenOn(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.updateSettings { it.copy(keepScreenOn = enabled) }
        }
    }

    fun toggleFastChannelSwitching(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.updateSettings { it.copy(fastChannelSwitching = enabled) }
        }
    }

    fun toggleHardwareAcceleration(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.updateSettings { it.copy(hardwareAcceleration = enabled) }
        }
    }

    fun setDefaultAspectRatio(mode: AspectRatioMode) {
        viewModelScope.launch {
            settingsRepository.updateSettings { it.copy(defaultAspectRatio = mode) }
        }
    }

    fun toggleStreamInfoOverlay(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.updateSettings { it.copy(showStreamInfoOverlay = enabled) }
        }
    }

    fun setPreferredMetadataSource(source: com.iptv.app.core.metadata.MetadataSource) {
        viewModelScope.launch {
            settingsRepository.updateSettings { it.copy(preferredMetadataSource = source) }
        }
    }

    fun setMetadataLanguage(lang: String) {
        viewModelScope.launch {
            settingsRepository.updateSettings { it.copy(metadataLanguage = lang) }
        }
    }

    fun setTraktClientId(id: String) {
        viewModelScope.launch {
            settingsRepository.updateSettings { it.copy(traktClientId = id) }
        }
    }

    fun setTvdbApiKey(key: String) {
        viewModelScope.launch {
            settingsRepository.updateSettings { it.copy(tvdbApiKey = key) }
        }
    }

    fun toggleInlineMetadataBadge(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.updateSettings { it.copy(showInlineMetadataBadge = enabled) }
        }
    }

    fun resetToDefaults() {
        viewModelScope.launch {
            settingsRepository.resetToDefaults()
        }
    }
}
