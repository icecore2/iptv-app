import Foundation
import AVFoundation
import IPTVCore

/// Real-time stream technical diagnostics and stream format info.
public struct StreamInfo: Hashable, Sendable {
    public var resolution: String
    public var bitrate: String
    public var videoCodec: String
    public var audioCodec: String
    public var frameRate: String
    public var streamFormat: String
    public var streamUrl: String
    public var bufferPercentage: Int

    public init(
        resolution: String = "Auto",
        bitrate: String = "Auto",
        videoCodec: String = "Auto",
        audioCodec: String = "Auto",
        frameRate: String = "Auto",
        streamFormat: String = "HLS (.m3u8)",
        streamUrl: String = "",
        bufferPercentage: Int = 0
    ) {
        self.resolution = resolution
        self.bitrate = bitrate
        self.videoCodec = videoCodec
        self.audioCodec = audioCodec
        self.frameRate = frameRate
        self.streamFormat = streamFormat
        self.streamUrl = streamUrl
        self.bufferPercentage = bufferPercentage
    }
}

/// Unified media playback engine protocol.
public protocol PlayerEngine: AnyObject, Sendable {
    func loadStream(url: URL, headers: [String: String])
    func play()
    func pause()
    func togglePlayPause()
    func seek(to timeInterval: TimeInterval)
    func seekBy(deltaSeconds: TimeInterval)
    func setMuted(_ isMuted: Bool)
    func setRate(_ rate: Float)
    func stop()
}
