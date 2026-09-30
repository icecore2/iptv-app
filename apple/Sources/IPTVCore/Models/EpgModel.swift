import Foundation

/// Represents a channel declared inside an XMLTV electronic programme guide.
public struct EpgChannel: Identifiable, Hashable, Codable, Sendable {
    public let id: String
    public let displayName: String
    public let iconUrl: String?

    public init(id: String, displayName: String, iconUrl: String? = nil) {
        self.id = id
        self.displayName = displayName
        self.iconUrl = iconUrl
    }
}

/// Represents a single TV programme or scheduled broadcast event in XMLTV.
public struct EpgProgramme: Identifiable, Hashable, Codable, Sendable {
    public var id: String { "\(channelId)_\(startEpochMillis)" }
    public let channelId: String
    public let title: String
    public let startEpochMillis: Int64
    public let stopEpochMillis: Int64
    public let descriptionText: String?
    public let category: String?
    public let iconUrl: String?

    public init(
        channelId: String,
        title: String,
        startEpochMillis: Int64,
        stopEpochMillis: Int64,
        descriptionText: String? = nil,
        category: String? = nil,
        iconUrl: String? = nil
    ) {
        self.channelId = channelId
        self.title = title
        self.startEpochMillis = startEpochMillis
        self.stopEpochMillis = stopEpochMillis
        self.descriptionText = descriptionText
        self.category = category
        self.iconUrl = iconUrl
    }

    /// Checks if this programme is currently airing live at the specified timestamp.
    public func isLive(timestampMillis: Int64 = Int64(Date().timeIntervalSince1970 * 1000)) -> Bool {
        return timestampMillis >= startEpochMillis && timestampMillis < stopEpochMillis
    }

    /// Calculates progress percentage (0.0 to 1.0) of this programme at the specified timestamp.
    public func progress(timestampMillis: Int64 = Int64(Date().timeIntervalSince1970 * 1000)) -> Float {
        guard stopEpochMillis > startEpochMillis else { return 0 }
        if timestampMillis <= startEpochMillis { return 0 }
        if timestampMillis >= stopEpochMillis { return 1 }
        return Float(timestampMillis - startEpochMillis) / Float(stopEpochMillis - startEpochMillis)
    }
}

/// Aggregates all channels and programmes parsed from an XMLTV guide.
public struct EpgData: Sendable {
    public var channels: [String: EpgChannel]
    public var programmes: [EpgProgramme]

    public init(
        channels: [String: EpgChannel] = [:],
        programmes: [EpgProgramme] = []
    ) {
        self.channels = channels
        self.programmes = programmes
    }
}
