import Foundation
import Combine
import IPTVCore

/// Persistence interface for managing global user preferences.
public protocol SettingsRepository: Sendable {
    func getSettings() async -> AppSettings
    func updateSettings(_ transform: @Sendable (AppSettings) -> AppSettings) async
    func resetToDefaults() async
}

/// Persistent UserDefaults implementation of SettingsRepository.
public actor UserDefaultsSettingsRepository: SettingsRepository {

    private let userDefaults: UserDefaults
    private let keySettings = "iptv_app_settings_json"

    public init(userDefaults: UserDefaults = .standard) {
        self.userDefaults = userDefaults
    }

    public func getSettings() -> AppSettings {
        guard let data = userDefaults.data(forKey: keySettings),
              let settings = try? JSONDecoder().decode(AppSettings.self, from: data) else {
            return AppSettings()
        }
        return settings
    }

    public func updateSettings(_ transform: @Sendable (AppSettings) -> AppSettings) {
        let current = getSettings()
        let updated = transform(current)
        if let data = try? JSONEncoder().encode(updated) {
            userDefaults.set(data, forKey: keySettings)
        }
    }

    public func resetToDefaults() {
        userDefaults.removeObject(forKey: keySettings)
    }
}

/// In-memory implementation of SettingsRepository for unit testing and SwiftUI Previews.
public actor InMemorySettingsRepository: SettingsRepository {
    private var settings: AppSettings

    public init(initialSettings: AppSettings = AppSettings()) {
        self.settings = initialSettings
    }

    public func getSettings() -> AppSettings {
        return settings
    }

    public func updateSettings(_ transform: @Sendable (AppSettings) -> AppSettings) {
        self.settings = transform(settings)
    }

    public func resetToDefaults() {
        self.settings = AppSettings()
    }
}
