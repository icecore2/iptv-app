import SwiftUI
import IPTVCore

/// Interactive video timeline scrubber supporting VOD progression and Live TV time-shift scrubbing with live edge indicators.
public struct EnhancedPlayerScrubber: View {
    public let isVod: Bool
    public let currentPosMs: Int64
    public let durationMs: Int64
    public let isAtLiveEdge: Bool
    public let timeShiftOffsetMs: Int64
    public let onSeek: (Int64) -> Void
    public let onGoToLive: () -> Void

    @State private var isDragging: Bool = false
    @State private var dragPositionMs: Int64 = 0

    public init(
        isVod: Bool,
        currentPosMs: Int64,
        durationMs: Int64,
        isAtLiveEdge: Bool = true,
        timeShiftOffsetMs: Int64 = 0,
        onSeek: @escaping (Int64) -> Void,
        onGoToLive: @escaping () -> Void
    ) {
        self.isVod = isVod
        self.currentPosMs = currentPosMs
        self.durationMs = durationMs
        self.isAtLiveEdge = isAtLiveEdge
        self.timeShiftOffsetMs = timeShiftOffsetMs
        self.onSeek = onSeek
        self.onGoToLive = onGoToLive
    }

    private var effectivePosMs: Int64 {
        isDragging ? dragPositionMs : currentPosMs
    }

    private var progressRatio: Double {
        guard durationMs > 0 else { return 0 }
        return min(1.0, max(0.0, Double(effectivePosMs) / Double(durationMs)))
    }

    public var body: some View {
        VStack(spacing: 6) {
            // Live Status or Time Indicators
            HStack {
                Text(formatTime(effectivePosMs))
                    .font(.caption.monospacedDigit())
                    .foregroundStyle(.white)

                Spacer()

                if isVod {
                    Text(formatTime(durationMs))
                        .font(.caption.monospacedDigit())
                        .foregroundStyle(.white.opacity(0.8))
                } else {
                    Button(action: onGoToLive) {
                        HStack(spacing: 4) {
                            Circle()
                                .fill(isAtLiveEdge ? Color.red : Color.gray)
                                .frame(width: 8, height: 8)
                            Text(isAtLiveEdge ? "LIVE" : "-\(formatTime(timeShiftOffsetMs)) (GO LIVE)")
                                .font(.caption2.bold())
                                .foregroundStyle(.white)
                        }
                        .padding(.horizontal, 8)
                        .padding(.vertical, 4)
                        .background(isAtLiveEdge ? Color.red.opacity(0.3) : Color.white.opacity(0.2))
                        .clipShape(Capsule())
                    }
                    .buttonStyle(.plain)
                }
            }

            // Interactive Progress Bar
            GeometryReader { geometry in
                ZStack(alignment: .leading) {
                    // Track background
                    Capsule()
                        .fill(Color.white.opacity(0.3))
                        .frame(height: 6)

                    // Filled progress
                    Capsule()
                        .fill(isVod ? Color.accentColor : (isAtLiveEdge ? Color.red : Color.orange))
                        .frame(width: max(0, geometry.size.width * progressRatio), height: 6)

                    // Scrubber Thumb
                    Circle()
                        .fill(Color.white)
                        .frame(width: isDragging ? 16 : 12, height: isDragging ? 16 : 12)
                        .offset(x: max(0, (geometry.size.width * progressRatio) - (isDragging ? 8 : 6)))
                        .shadow(radius: 2)
                }
                .contentShape(Rectangle())
                .gesture(
                    DragGesture(minimumDistance: 0)
                        .onChanged { value in
                            isDragging = true
                            let ratio = min(1.0, max(0.0, value.location.x / geometry.size.width))
                            dragPositionMs = Int64(ratio * Double(durationMs))
                        }
                        .onEnded { value in
                            isDragging = false
                            let ratio = min(1.0, max(0.0, value.location.x / geometry.size.width))
                            let targetMs = Int64(ratio * Double(durationMs))
                            onSeek(targetMs)
                        }
                )
            }
            .frame(height: 16)
        }
    }

    private func formatTime(_ millis: Int64) -> String {
        let totalSec = max(0, millis / 1000)
        let hours = totalSec / 3600
        let minutes = (totalSec % 3600) / 60
        let seconds = totalSec % 60

        if hours > 0 {
            return String(format: "%d:%02d:%02d", hours, minutes, seconds)
        } else {
            return String(format: "%02d:%02d", minutes, seconds)
        }
    }
}
