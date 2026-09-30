import Foundation

/// State representation of the live time-shift buffer.
public struct TimeShiftState: Equatable, Sendable {
    public var liveSessionStartTimeMs: Int64
    public var liveSessionDurationMs: Int64
    public var livePositionFromStartMs: Int64
    public var isAtLiveEdge: Bool
    public var timeShiftOffsetMs: Int64
    public var canGoBackToStart: Bool

    public init(
        liveSessionStartTimeMs: Int64 = 0,
        liveSessionDurationMs: Int64 = 0,
        livePositionFromStartMs: Int64 = 0,
        isAtLiveEdge: Bool = true,
        timeShiftOffsetMs: Int64 = 0,
        canGoBackToStart: Bool = false
    ) {
        self.liveSessionStartTimeMs = liveSessionStartTimeMs
        self.liveSessionDurationMs = liveSessionDurationMs
        self.livePositionFromStartMs = livePositionFromStartMs
        self.isAtLiveEdge = isAtLiveEdge
        self.timeShiftOffsetMs = timeShiftOffsetMs
        self.canGoBackToStart = canGoBackToStart
    }
}

/// Controller responsible for managing live stream time-shift rewind, DVR window, and live edge detection.
public final class TimeShiftBufferController: Sendable {

    public init() {}

    /// Calculates updated time-shift state based on playback session elapsed duration and position.
    public func updateProgress(sessionDurationMs: Int64, positionFromStartMs: Int64) -> TimeShiftState {
        let safeSessionDur = max(0, sessionDurationMs)
        let safePos = min(max(0, positionFromStartMs), safeSessionDur)
        let offset = max(0, safeSessionDur - safePos)
        let atLive = offset <= 3000 // Within 3 seconds of the live edge
        let canGoBack = safeSessionDur >= 5000

        return TimeShiftState(
            liveSessionStartTimeMs: 0,
            liveSessionDurationMs: safeSessionDur,
            livePositionFromStartMs: safePos,
            isAtLiveEdge: atLive,
            timeShiftOffsetMs: offset,
            canGoBackToStart: canGoBack
        )
    }

    /// Resets the live buffer for a freshly tuned channel.
    public func resetLiveBuffer() -> TimeShiftState {
        return TimeShiftState(
            liveSessionStartTimeMs: Int64(Date().timeIntervalSince1970 * 1000),
            liveSessionDurationMs: 0,
            livePositionFromStartMs: 0,
            isAtLiveEdge: true,
            timeShiftOffsetMs: 0,
            canGoBackToStart: false
        )
    }
}
