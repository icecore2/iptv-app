import Foundation
import MediaPlayer
import IPTVCore

/// Coordinates system Lock Screen, Control Center, and macOS Menu Bar Now Playing integration.
@MainActor
public final class NowPlayingController {

    public static let shared = NowPlayingController()

    public var onPlayPauseToggled: (() -> Void)?
    public var onNextChannel: (() -> Void)?
    public var onPreviousChannel: (() -> Void)?
    public var onSkip: ((TimeInterval) -> Void)?

    private init() {
        setupRemoteCommands()
    }

    /// Updates Lock Screen & Menu Bar Now Playing information.
    public func updateNowPlaying(
        channelName: String,
        programmeTitle: String?,
        isLive: Bool,
        duration: TimeInterval = 0,
        elapsedTime: TimeInterval = 0,
        isPlaying: Bool = true
    ) {
        var info: [String: Any] = [:]
#if os(iOS) || os(tvOS) || os(watchOS) || targetEnvironment(macCatalyst)
        info[MPMediaItemPropertyTitle] = title
        info[MPMediaItemPropertyArtist] = channelName
        info[MPNowPlayingInfoPropertyIsLiveStream] = isLive

        if !isLive && duration > 0 {
            info[MPMediaItemPropertyPlaybackDuration] = duration
            info[MPNowPlayingInfoPropertyElapsedPlaybackTime] = elapsedTime
        }
        info[MPNowPlayingInfoPropertyPlaybackRate] = isPlaying ? 1.0 : 0.0
#elseif os(macOS)
        info[MPNowPlayingInfoPropertyElapsedPlaybackTime] = elapsedTime
        info[MPNowPlayingInfoPropertyPlaybackRate] = isPlaying ? 1.0 : 0.0
#endif

        MPNowPlayingInfoCenter.default().nowPlayingInfo = info
    }

    /// Clears Now Playing state when playback terminates.
    public func clearNowPlaying() {
        MPNowPlayingInfoCenter.default().nowPlayingInfo = nil
    }

    private func setupRemoteCommands() {
        let center = MPRemoteCommandCenter.shared()

        center.playCommand.addTarget { [weak self] _ in
            self?.onPlayPauseToggled?()
            return .success
        }

        center.pauseCommand.addTarget { [weak self] _ in
            self?.onPlayPauseToggled?()
            return .success
        }

        center.togglePlayPauseCommand.addTarget { [weak self] _ in
            self?.onPlayPauseToggled?()
            return .success
        }

        center.nextTrackCommand.addTarget { [weak self] _ in
            self?.onNextChannel?()
            return .success
        }

        center.previousTrackCommand.addTarget { [weak self] _ in
            self?.onPreviousChannel?()
            return .success
        }

        center.skipForwardCommand.preferredIntervals = [10]
        center.skipForwardCommand.addTarget { [weak self] event in
            if let skipEvent = event as? MPSkipIntervalCommandEvent {
                self?.onSkip?(skipEvent.interval)
            } else {
                self?.onSkip?(10)
            }
            return .success
        }

        center.skipBackwardCommand.preferredIntervals = [10]
        center.skipBackwardCommand.addTarget { [weak self] event in
            if let skipEvent = event as? MPSkipIntervalCommandEvent {
                self?.onSkip?(-skipEvent.interval)
            } else {
                self?.onSkip?(-10)
            }
            return .success
        }
    }
}
