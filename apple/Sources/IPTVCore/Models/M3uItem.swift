import Foundation

/// Represents a single IPTV channel or VOD media item parsed from an M3U playlist.
public struct M3uItem: Identifiable, Hashable, Codable, Sendable {
    public let id: String
    public let name: String
    public let streamUrl: String
    public var group: String
    public var logoUrl: String?
    public var tvgId: String?
    public var tvgName: String?
    public var headers: [String: String]
    public var isRadio: Bool
    public var catchup: String?
    public var catchupSource: String?
    public var catchupDays: Int?
    public var isCatchup: Bool

    public init(
        id: String,
        name: String,
        streamUrl: String,
        group: String = "General",
        logoUrl: String? = nil,
        tvgId: String? = nil,
        tvgName: String? = nil,
        headers: [String: String] = [:],
        isRadio: Bool = false,
        catchup: String? = nil,
        catchupSource: String? = nil,
        catchupDays: Int? = nil,
        isCatchup: Bool = false
    ) {
        self.id = id
        self.name = name
        self.streamUrl = streamUrl
        self.group = group
        self.logoUrl = logoUrl
        self.tvgId = tvgId
        self.tvgName = tvgName
        self.headers = headers
        self.isRadio = isRadio
        self.catchup = catchup
        self.catchupSource = catchupSource
        self.catchupDays = catchupDays
        self.isCatchup = isCatchup
    }

    /// Determines whether this stream represents Video On Demand (movie/series/catchup archive)
    /// rather than a live 24/7 broadcast channel.
    public var isVod: Bool {
        if isCatchup { return true }
        let lowerUrl = streamUrl.lowercased()
        if lowerUrl.hasSuffix(".mp4") || lowerUrl.hasSuffix(".mkv") { return true }
        let g = group.lowercased()
        return g.contains("movie") || g.contains("vod") || g.contains("cinema") ||
               g.contains("film") || g.contains("catchup")
    }
}

/// Represents a fully parsed M3U playlist with all items, categories, and header tags.
public struct M3uPlaylist: Hashable, Codable, Sendable {
    public var items: [M3uItem]
    public var groups: [String]
    public var epgUrl: String?
    public var headerAttributes: [String: String]

    public init(
        items: [M3uItem] = [],
        groups: [String] = [],
        epgUrl: String? = nil,
        headerAttributes: [String: String] = [:]
    ) {
        self.items = items
        self.groups = groups
        self.epgUrl = epgUrl
        self.headerAttributes = headerAttributes
    }
}
