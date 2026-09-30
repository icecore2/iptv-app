import Foundation

/// Matches M3U channels with their corresponding XMLTV EPG data using intelligent fuzzy normalization.
public final class EpgMatcher: Sendable {

    public let epgData: EpgData
    private let programmesByChannel: [String: [EpgProgramme]]
    private let normalizedChannelIndex: [String: String]

    private static let bracketsRegex: NSRegularExpression? = {
        try? NSRegularExpression(pattern: #"\\[.*?\\]|\\(.*?\\)"#, options: [])
    }()

    private static let qualityRegex: NSRegularExpression? = {
        try? NSRegularExpression(pattern: #"\\b(hd|fhd|uhd|4k|sd|hevc|1080p|720p)\\b"#, options: [.caseInsensitive])
    }()

    private static let punctuationRegex: NSRegularExpression? = {
        try? NSRegularExpression(pattern: #"[^a-z0-9]"#, options: [])
    }()

    public init(epgData: EpgData) {
        self.epgData = epgData

        var grouped: [String: [EpgProgramme]] = [:]
        for programme in epgData.programmes {
            grouped[programme.channelId, default: []].append(programme)
        }
        for (k, v) in grouped {
            grouped[k] = v.sorted(by: { $0.startEpochMillis < $1.startEpochMillis })
        }
        self.programmesByChannel = grouped

        var index: [String: String] = [:]
        for (channelId, channel) in epgData.channels {
            index[channelId.lowercased()] = channelId
            let norm = Self.normalize(channel.displayName)
            if !norm.isEmpty {
                index[norm] = channelId
            }
        }
        self.normalizedChannelIndex = index
    }

    /// Finds the corresponding XMLTV channel ID for an M3U item.
    public func findEpgChannelId(channel: M3uItem) -> String? {
        // 1. Direct tvgId match
        if let tvgId = channel.tvgId, !tvgId.isEmpty {
            if epgData.channels[tvgId] != nil { return tvgId }
            let lower = tvgId.lowercased()
            if let matched = normalizedChannelIndex[lower] { return matched }
        }

        // 2. Direct tvgName normalized match
        if let tvgName = channel.tvgName, !tvgName.isEmpty {
            let norm = Self.normalize(tvgName)
            if let matched = normalizedChannelIndex[norm] { return matched }
        }

        // 3. Channel display name normalized match
        let nameNorm = Self.normalize(channel.name)
        if let matched = normalizedChannelIndex[nameNorm] {
            return matched
        }

        return nil
    }

    /// Retrieves full programme schedule for a given channel.
    public func getSchedule(channel: M3uItem) -> [EpgProgramme] {
        guard let epgChannelId = findEpgChannelId(channel: channel) else {
            return []
        }
        return programmesByChannel[epgChannelId] ?? []
    }

    /// Returns the currently airing live programme.
    public func getCurrentProgramme(
        channel: M3uItem,
        timestamp: Int64 = Int64(Date().timeIntervalSince1970 * 1000)
    ) -> EpgProgramme? {
        let schedule = getSchedule(channel: channel)
        return schedule.first(where: { $0.isLive(timestampMillis: timestamp) })
    }

    /// Returns the upcoming next programme after the current one finishes.
    public func getNextProgramme(
        channel: M3uItem,
        timestamp: Int64 = Int64(Date().timeIntervalSince1970 * 1000)
    ) -> EpgProgramme? {
        let schedule = getSchedule(channel: channel)
        let current = getCurrentProgramme(channel: channel, timestamp: timestamp)
        let cutoff = current?.stopEpochMillis ?? timestamp
        return schedule.first(where: { $0.startEpochMillis >= cutoff })
    }

    /// Enriches an M3U item into a ChannelWithEpg containing live and next programme metadata.
    public func enrichChannel(
        channel: M3uItem,
        timestamp: Int64 = Int64(Date().timeIntervalSince1970 * 1000)
    ) -> ChannelWithEpg {
        let current = getCurrentProgramme(channel: channel, timestamp: timestamp)
        let next = getNextProgramme(channel: channel, timestamp: timestamp)
        let progress = current?.progress(timestampMillis: timestamp) ?? 0
        return ChannelWithEpg(
            channel: channel,
            currentProgramme: current,
            nextProgramme: next,
            progress: progress
        )
    }

    /// Normalizes channel strings for fuzzy matching (removes tags like [UK], (US), 1080p, HD, punctuation).
    public static func normalize(_ name: String) -> String {
        var str = name.lowercased()

        if let regex = bracketsRegex {
            let range = NSRange(str.startIndex..<str.endIndex, in: str)
            str = regex.stringByReplacingMatches(in: str, options: [], range: range, withTemplate: "")
        }
        if let regex = qualityRegex {
            let range = NSRange(str.startIndex..<str.endIndex, in: str)
            str = regex.stringByReplacingMatches(in: str, options: [], range: range, withTemplate: "")
        }
        if let regex = punctuationRegex {
            let range = NSRange(str.startIndex..<str.endIndex, in: str)
            str = regex.stringByReplacingMatches(in: str, options: [], range: range, withTemplate: "")
        }

        return str.trimmingCharacters(in: .whitespacesAndNewlines)
    }
}
