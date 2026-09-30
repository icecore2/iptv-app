import Foundation
import IPTVCore

/// Persistence interface for saved IPTV profiles and favorite channels.
public protocol SavedPlaylistRepository: Sendable {
    func getSavedPlaylists() async -> [SavedPlaylistPair]
    @discardableResult
    func savePlaylist(_ pair: SavedPlaylistPair) async -> SavedPlaylistPair
    func updatePlaylist(_ pair: SavedPlaylistPair) async
    func deletePlaylist(id: String) async
    func getActivePairId() async -> String?
    func setActivePairId(_ id: String?) async
    func updateFavorites(pairId: String, favoriteIds: Set<String>) async
}

/// Persistent UserDefaults implementation of SavedPlaylistRepository.
public actor UserDefaultsSavedPlaylistRepository: SavedPlaylistRepository {

    private let userDefaults: UserDefaults
    private let keyPlaylists = "iptv_saved_playlists_json"
    private let keyActivePairId = "active_playlist_pair_id"
    private let keyInitialized = "has_initialized_defaults"

    public init(userDefaults: UserDefaults = .standard) {
        self.userDefaults = userDefaults
    }

    public func getSavedPlaylists() -> [SavedPlaylistPair] {
        let hasInit = userDefaults.bool(forKey: keyInitialized)
        guard let data = userDefaults.data(forKey: keyPlaylists) else {
            if !hasInit {
                let initial = [SampleDataProvider.defaultSamplePair]
                saveListToDefaults(initial)
                userDefaults.set(true, forKey: keyInitialized)
                return initial
            }
            return []
        }

        do {
            return try JSONDecoder().decode([SavedPlaylistPair].self, from: data)
        } catch {
            return []
        }
    }

    @discardableResult
    public func savePlaylist(_ pair: SavedPlaylistPair) -> SavedPlaylistPair {
        var current = getSavedPlaylists()
        if let idx = current.firstIndex(where: { $0.id == pair.id }) {
            current[idx] = pair
        } else {
            current.append(pair)
        }
        saveListToDefaults(current)
        return pair
    }

    public func updatePlaylist(_ pair: SavedPlaylistPair) {
        var current = getSavedPlaylists()
        if let idx = current.firstIndex(where: { $0.id == pair.id }) {
            current[idx] = pair
            saveListToDefaults(current)
        }
    }

    public func deletePlaylist(id: String) {
        var current = getSavedPlaylists()
        current.removeAll(where: { $0.id == id })
        saveListToDefaults(current)

        if getActivePairId() == id {
            setActivePairId(nil)
        }
    }

    public func getActivePairId() -> String? {
        userDefaults.string(forKey: keyActivePairId)
    }

    public func setActivePairId(_ id: String?) {
        if let id = id {
            userDefaults.set(id, forKey: keyActivePairId)
        } else {
            userDefaults.removeObject(forKey: keyActivePairId)
        }
    }

    public func updateFavorites(pairId: String, favoriteIds: Set<String>) {
        var current = getSavedPlaylists()
        if let idx = current.firstIndex(where: { $0.id == pairId }) {
            var updated = current[idx]
            updated.favoriteIds = favoriteIds
            current[idx] = updated
            saveListToDefaults(current)
        }
    }

    private func saveListToDefaults(_ list: [SavedPlaylistPair]) {
        if let data = try? JSONEncoder().encode(list) {
            userDefaults.set(data, forKey: keyPlaylists)
        }
    }
}

/// In-memory repository implementation for unit tests and SwiftUI Previews.
public actor InMemorySavedPlaylistRepository: SavedPlaylistRepository {
    private var playlists: [SavedPlaylistPair]
    private var activeId: String?

    public init(
        initialList: [SavedPlaylistPair] = [SampleDataProvider.defaultSamplePair],
        activeId: String? = SampleDataProvider.defaultSamplePair.id
    ) {
        self.playlists = initialList
        self.activeId = activeId
    }

    public func getSavedPlaylists() -> [SavedPlaylistPair] {
        return playlists
    }

    @discardableResult
    public func savePlaylist(_ pair: SavedPlaylistPair) -> SavedPlaylistPair {
        if let idx = playlists.firstIndex(where: { $0.id == pair.id }) {
            playlists[idx] = pair
        } else {
            playlists.append(pair)
        }
        return pair
    }

    public func updatePlaylist(_ pair: SavedPlaylistPair) {
        if let idx = playlists.firstIndex(where: { $0.id == pair.id }) {
            playlists[idx] = pair
        }
    }

    public func deletePlaylist(id: String) {
        playlists.removeAll(where: { $0.id == id })
        if activeId == id {
            activeId = nil
        }
    }

    public func getActivePairId() -> String? {
        return activeId
    }

    public func setActivePairId(_ id: String?) {
        self.activeId = id
    }

    public func updateFavorites(pairId: String, favoriteIds: Set<String>) {
        if let idx = playlists.firstIndex(where: { $0.id == pairId }) {
            var updated = playlists[idx]
            updated.favoriteIds = favoriteIds
            playlists[idx] = updated
        }
    }
}
