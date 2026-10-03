import Foundation
import Combine
import IPTVCore
import IPTVData

#if canImport(UIKit)
import UIKit
#endif

@MainActor
public final class SettingsViewModel: ObservableObject {

    @Published public var settings: AppSettings = AppSettings()
    @Published public var usedStorageBytes: Int64 = 0
    @Published public var freeStorageBytes: Int64 = 0

    private let settingsRepository: SettingsRepository

    public init(settingsRepository: SettingsRepository = UserDefaultsSettingsRepository()) {
        self.settingsRepository = settingsRepository

        Task {
            await loadSettings()
            refreshBufferStorage()
        }
    }

    public func loadSettings() async {
        self.settings = await settingsRepository.getSettings()
        applyScreenIdleTimer(self.settings.keepScreenOn)
    }

    public func toggleChannelLogos(_ enabled: Bool) {
        settings.showChannelLogos = enabled
        saveSettings { $0.showChannelLogos = enabled }
    }

    public func togglePagination(_ enabled: Bool) {
        settings.enablePagination = enabled
        saveSettings { $0.enablePagination = enabled }
    }

    public func setPageSize(_ size: Int) {
        settings.pageSize = size
        saveSettings { $0.pageSize = size }
    }

    public func toggleShowEpgInList(_ enabled: Bool) {
        settings.showEpgInList = enabled
        saveSettings { $0.showEpgInList = enabled }
    }

    public func toggleAutoLoadLastPlaylist(_ enabled: Bool) {
        settings.autoLoadLastPlaylist = enabled
        saveSettings { $0.autoLoadLastPlaylist = enabled }
    }

    public func setBufferDuration(_ seconds: Int) {
        settings.bufferDurationSeconds = seconds
        saveSettings { $0.bufferDurationSeconds = seconds }
    }

    public func setBufferStorageLimit(_ limitMb: Int) {
        settings.bufferStorageLimitMb = limitMb
        saveSettings { $0.bufferStorageLimitMb = limitMb }
    }

    public func clearBufferCache() {
        PlaybackCacheManager.shared.clearCache()
        refreshBufferStorage()
    }

    public func refreshBufferStorage() {
        self.usedStorageBytes = PlaybackCacheManager.shared.getUsedStorageBytes()
        self.freeStorageBytes = PlaybackCacheManager.shared.getFreeDiskSpaceBytes()
    }

    public func toggleKeepScreenOn(_ enabled: Bool) {
        settings.keepScreenOn = enabled
        applyScreenIdleTimer(enabled)
        saveSettings { $0.keepScreenOn = enabled }
    }

    public func toggleFastChannelSwitching(_ enabled: Bool) {
        settings.fastChannelSwitching = enabled
        saveSettings { $0.fastChannelSwitching = enabled }
    }

    public func toggleHardwareAcceleration(_ enabled: Bool) {
        settings.hardwareAcceleration = enabled
        saveSettings { $0.hardwareAcceleration = enabled }
    }

    public func setDefaultAspectRatio(_ mode: AspectRatioMode) {
        settings.defaultAspectRatio = mode
        saveSettings { $0.defaultAspectRatio = mode }
    }

    public func toggleStreamInfoOverlay(_ enabled: Bool) {
        settings.showStreamInfoOverlay = enabled
        saveSettings { $0.showStreamInfoOverlay = enabled }
    }

    public func setPreferredMetadataSource(_ source: MetadataSource) {
        settings.preferredMetadataSource = source
        saveSettings { $0.preferredMetadataSource = source }
    }

    public func setMetadataLanguage(_ language: String) {
        settings.metadataLanguage = language
        saveSettings { $0.metadataLanguage = language }
    }

    public func setTraktClientId(_ id: String) {
        settings.traktClientId = id
        saveSettings { $0.traktClientId = id }
    }

    public func setTvdbApiKey(_ key: String) {
        settings.tvdbApiKey = key
        saveSettings { $0.tvdbApiKey = key }
    }

    public func toggleShowInlineMetadataBadge(_ enabled: Bool) {
        settings.showInlineMetadataBadge = enabled
        saveSettings { $0.showInlineMetadataBadge = enabled }
    }

    public func resetToDefaults() async {
        await settingsRepository.resetToDefaults()
        self.settings = AppSettings()
        applyScreenIdleTimer(settings.keepScreenOn)
    }

    private func saveSettings(_ mutate: @escaping @Sendable (inout AppSettings) -> Void) {
        Task {
            await settingsRepository.updateSettings { current in
                var updated = current
                mutate(&updated)
                return updated
            }
        }
    }

    private func applyScreenIdleTimer(_ keepScreenOn: Bool) {
        #if canImport(UIKit)
        UIApplication.shared.isIdleTimerDisabled = keepScreenOn
        #endif
    }
}
