import Foundation

/// Represents a configured playlist and EPG source profile.
public struct SavedPlaylistPair: Identifiable, Hashable, Codable, Sendable {
    public let id: String
    public var name: String
    public var playlistUrl: String
    public var epgUrl: String?
    public var isSample: Bool
    public var createdAt: Int64
    public var favoriteIds: Set<String>

    public init(
        id: String = UUID().uuidString,
        name: String,
        playlistUrl: String,
        epgUrl: String? = nil,
        isSample: Bool = false,
        createdAt: Int64 = Int64(Date().timeIntervalSince1970 * 1000),
        favoriteIds: Set<String> = []
    ) {
        self.id = id
        self.name = name
        self.playlistUrl = playlistUrl
        self.epgUrl = epgUrl
        self.isSample = isSample
        self.createdAt = createdAt
        self.favoriteIds = favoriteIds
    }
}
