import Foundation

/// Combines an M3U channel with its currently airing and upcoming EPG schedule info.
public struct ChannelWithEpg: Identifiable, Hashable, Sendable {
    public var id: String { channel.id }
    public let channel: M3uItem
    public let currentProgramme: EpgProgramme?
    public let nextProgramme: EpgProgramme?
    public let progress: Float

    public init(
        channel: M3uItem,
        currentProgramme: EpgProgramme? = nil,
        nextProgramme: EpgProgramme? = nil,
        progress: Float = 0
    ) {
        self.channel = channel
        self.currentProgramme = currentProgramme
        self.nextProgramme = nextProgramme
        self.progress = progress
    }
}
