import Foundation
import AVFoundation
import Combine
import IPTVCore

#if canImport(AVKit)
import AVKit
#endif

/// Thread-safe AVPlayer media playback engine implementation with Picture-in-Picture and stream diagnostics.
@MainActor
public final class AVPlayerEngine: NSObject, ObservableObject {

    public let player = AVPlayer()

    @Published public private(set) var isPlaying: Bool = false
    @Published public private(set) var isBuffering: Bool = false
    @Published public private(set) var errorMessage: String? = nil
    @Published public private(set) var streamInfo: StreamInfo = StreamInfo()
    @Published public private(set) var currentTime: TimeInterval = 0
    @Published public private(set) var duration: TimeInterval = 0
    @Published public private(set) var isLiveStream: Bool = false

    private var timeObserverToken: Any?
    private var itemStatusObserver: NSKeyValueObservation?
    private var itemPlaybackBufferEmptyObserver: NSKeyValueObservation?
    private var itemPlaybackLikelyToKeepUpObserver: NSKeyValueObservation?
    private var itemTimeControlStatusObserver: NSKeyValueObservation?

    public override init() {
        super.init()
        setupPlayerObservers()
    }

    deinit {
        if let token = timeObserverToken {
            player.removeTimeObserver(token)
        }
    }

    /// Loads and begins playback of a stream URL with custom HTTP request headers.
    public func loadStream(
        url: URL,
        headers: [String: String] = [:],
        isVod: Bool = false
    ) {
        stop()

        var options: [String: Any] = [
            "AVURLAssetHTTPHeaderFieldsKey": headers
        ]
        let asset = AVURLAsset(url: url, options: options)
        let playerItem = AVPlayerItem(asset: asset)

        // Tune buffer forward duration
        playerItem.preferredForwardBufferDuration = 15

        observePlayerItem(playerItem)
        player.replaceCurrentItem(with: playerItem)

        let format = Self.detectStreamFormat(url: url)
        self.streamInfo = StreamInfo(
            streamFormat: format,
            streamUrl: url.absoluteString
        )
        self.isLiveStream = !isVod
        self.isBuffering = true
        self.errorMessage = nil

        player.play()
    }

    public func play() {
        player.play()
        isPlaying = true
    }

    public func pause() {
        player.pause()
        isPlaying = false
    }

    public func togglePlayPause() {
        if isPlaying {
            pause()
        } else {
            play()
        }
    }

    public func seek(to timeInterval: TimeInterval) {
        let cmTime = CMTime(seconds: timeInterval, preferredTimescale: 600)
        player.seek(to: cmTime, toleranceBefore: .zero, toleranceAfter: .zero)
    }

    public func seekBy(deltaSeconds: TimeInterval) {
        let target = max(0, currentTime + deltaSeconds)
        seek(to: target)
    }

    public func setMuted(_ isMuted: Bool) {
        player.isMuted = isMuted
    }

    public func setRate(_ rate: Float) {
        player.rate = rate
    }

    public func stop() {
        player.pause()
        player.replaceCurrentItem(with: nil)
        itemStatusObserver?.invalidate()
        itemPlaybackBufferEmptyObserver?.invalidate()
        itemPlaybackLikelyToKeepUpObserver?.invalidate()
        itemTimeControlStatusObserver?.invalidate()
        isPlaying = false
        isBuffering = false
        currentTime = 0
        duration = 0
    }

    private func setupPlayerObservers() {
        // Periodic time observer every 250ms
        let interval = CMTime(seconds: 0.25, preferredTimescale: 600)
        timeObserverToken = player.addPeriodicTimeObserver(forInterval: interval, queue: .main) { [weak self] time in
            guard let self = self else { return }
            self.currentTime = time.seconds
            if let currentItem = self.player.currentItem {
                let itemDur = currentItem.duration.seconds
                if !itemDur.isNaN && !itemDur.isInfinite {
                    self.duration = itemDur
                }
            }
            self.updateStreamDiagnostics()
        }
    }

    private func observePlayerItem(_ item: AVPlayerItem) {
        itemStatusObserver = item.observe(\.status, options: [.new]) { [weak self] item, _ in
            Task { @MainActor [weak self] in
                guard let self = self else { return }
                switch item.status {
                case .readyToPlay:
                    self.isBuffering = false
                    self.isPlaying = (self.player.timeControlStatus == .playing)
                    self.updateStreamDiagnostics()
                case .failed:
                    self.isBuffering = false
                    self.isPlaying = false
                    self.errorMessage = item.error?.localizedDescription ?? "Playback error"
                default:
                    break
                }
            }
        }

        itemPlaybackBufferEmptyObserver = item.observe(\.isPlaybackBufferEmpty, options: [.new]) { [weak self] item, _ in
            Task { @MainActor [weak self] in
                if item.isPlaybackBufferEmpty {
                    self?.isBuffering = true
                }
            }
        }

        itemPlaybackLikelyToKeepUpObserver = item.observe(\.isPlaybackLikelyToKeepUp, options: [.new]) { [weak self] item, _ in
            Task { @MainActor [weak self] in
                if item.isPlaybackLikelyToKeepUp {
                    self?.isBuffering = false
                }
            }
        }

        itemTimeControlStatusObserver = player.observe(\.timeControlStatus, options: [.new]) { [weak self] player, _ in
            Task { @MainActor [weak self] in
                self?.isPlaying = (player.timeControlStatus == .playing)
                self?.isBuffering = (player.timeControlStatus == .waitingToPlayAtSpecifiedRate)
            }
        }
    }

    private func updateStreamDiagnostics() {
        guard let currentItem = player.currentItem else { return }

        // Track video dimensions
        var res = streamInfo.resolution
        var codec = streamInfo.videoCodec
        var fps = streamInfo.frameRate

        if let track = currentItem.tracks.first(where: { $0.assetTrack?.mediaType == .video })?.assetTrack {
            let size = track.naturalSize
            if size.width > 0 && size.height > 0 {
                res = "\(Int(size.width))x\(Int(size.height))"
            }
            if track.nominalFrameRate > 0 {
                fps = "\(Int(round(track.nominalFrameRate))) fps"
            }
            if let desc = track.formatDescriptions.first {
                let mediaSubtype = CMFormatDescriptionGetMediaSubType(desc as! CMFormatDescription)
                codec = Self.fourCCToString(mediaSubtype)
            }
        }

        // Bitrate calculation
        var bitrate = streamInfo.bitrate
        if let event = currentItem.accessLog()?.events.last {
            if event.indicatedBitrate > 0 {
                let mbps = event.indicatedBitrate / 1_000_000.0
                bitrate = String(format: "%.1f Mbps", mbps)
            } else if event.observedBitrate > 0 {
                let mbps = event.observedBitrate / 1_000_000.0
                bitrate = String(format: "%.1f Mbps", mbps)
            }
        }

        // Buffer percentage
        var bufPercent = 0
        if let timeRange = currentItem.loadedTimeRanges.first?.timeRangeValue {
            let loaded = timeRange.duration.seconds
            bufPercent = min(100, max(0, Int((loaded / 15.0) * 100.0)))
        }

        self.streamInfo = StreamInfo(
            resolution: res,
            bitrate: bitrate,
            videoCodec: codec,
            audioCodec: streamInfo.audioCodec,
            frameRate: fps,
            streamFormat: streamInfo.streamFormat,
            streamUrl: streamInfo.streamUrl,
            bufferPercentage: bufPercent
        )
    }

    private static func detectStreamFormat(url: URL) -> String {
        let path = url.path.lowercased()
        if path.hasSuffix(".m3u8") { return "HLS (.m3u8)" }
        if path.hasSuffix(".mpd") { return "DASH (.mpd)" }
        if path.hasSuffix(".mp4") { return "MP4 Video" }
        if path.hasSuffix(".mkv") { return "MKV Video" }
        if path.hasSuffix(".ts") { return "MPEG-TS (.ts)" }
        return "Live Stream"
    }

    private static func fourCCToString(_ fourCC: FourCharCode) -> String {
        let bytes: [CChar] = [
            CChar((fourCC >> 24) & 0xff),
            CChar((fourCC >> 16) & 0xff),
            CChar((fourCC >> 8) & 0xff),
            CChar(fourCC & 0xff),
            0
        ]
        return String(cString: bytes).trimmingCharacters(in: .whitespaces)
    }
}
