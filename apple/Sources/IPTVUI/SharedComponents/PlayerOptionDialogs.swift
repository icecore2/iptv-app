import SwiftUI
import IPTVCore

/// Aspect Ratio selection dialog sheet.
public struct AspectRatioDialog: View {
    public let currentMode: AspectRatioMode
    public let onSelect: (AspectRatioMode) -> Void
    public let onDismiss: () -> Void

    public init(currentMode: AspectRatioMode, onSelect: @escaping (AspectRatioMode) -> Void, onDismiss: @escaping () -> Void) {
        self.currentMode = currentMode
        self.onSelect = onSelect
        self.onDismiss = onDismiss
    }

    public var body: some View {
        VStack(spacing: 12) {
            Text("Aspect Ratio Mode")
                .font(.headline)

            ForEach(AspectRatioMode.allCases, id: \.self) { mode in
                Button(action: {
                    onSelect(mode)
                    onDismiss()
                }) {
                    HStack {
                        Text(mode.displayName)
                        Spacer()
                        if currentMode == mode {
                            Image(systemName: "checkmark")
                                .foregroundStyle(Color.accentColor)
                        }
                    }
                    .padding()
                    .background(Color.secondary.opacity(0.1))
                    .clipShape(RoundedRectangle(cornerRadius: 10))
                }
                .buttonStyle(.plain)
            }
        }
        .padding(20)
        .frame(maxWidth: 340)
        .background(.ultraThickMaterial)
        .clipShape(RoundedRectangle(cornerRadius: 16))
    }
}

/// Playback speed selection dialog sheet.
public struct PlaybackSpeedDialog: View {
    public let currentSpeed: Float
    public let onSelect: (Float) -> Void
    public let onDismiss: () -> Void

    private let speeds: [Float] = [0.5, 0.75, 1.0, 1.25, 1.5, 2.0]

    public init(currentSpeed: Float, onSelect: @escaping (Float) -> Void, onDismiss: @escaping () -> Void) {
        self.currentSpeed = currentSpeed
        self.onSelect = onSelect
        self.onDismiss = onDismiss
    }

    public var body: some View {
        VStack(spacing: 12) {
            Text("Playback Speed")
                .font(.headline)

            ForEach(speeds, id: \.self) { speed in
                Button(action: {
                    onSelect(speed)
                    onDismiss()
                }) {
                    HStack {
                        Text(String(format: "%.2fx", speed))
                        Spacer()
                        if abs(currentSpeed - speed) < 0.01 {
                            Image(systemName: "checkmark")
                                .foregroundStyle(Color.accentColor)
                        }
                    }
                    .padding()
                    .background(Color.secondary.opacity(0.1))
                    .clipShape(RoundedRectangle(cornerRadius: 10))
                }
                .buttonStyle(.plain)
            }
        }
        .padding(20)
        .frame(maxWidth: 340)
        .background(.ultraThickMaterial)
        .clipShape(RoundedRectangle(cornerRadius: 16))
    }
}

/// Sleep timer selection dialog sheet.
public struct SleepTimerDialog: View {
    public let currentRemainingMinutes: Int?
    public let onSetTimer: (Int) -> Void
    public let onCancelTimer: () -> Void
    public let onDismiss: () -> Void

    private let options = [15, 30, 45, 60, 90, 120]

    public init(
        currentRemainingMinutes: Int?,
        onSetTimer: @escaping (Int) -> Void,
        onCancelTimer: @escaping () -> Void,
        onDismiss: @escaping () -> Void
    ) {
        self.currentRemainingMinutes = currentRemainingMinutes
        self.onSetTimer = onSetTimer
        self.onCancelTimer = onCancelTimer
        self.onDismiss = onDismiss
    }

    public var body: some View {
        VStack(spacing: 12) {
            Text("Sleep Timer")
                .font(.headline)

            if let remaining = currentRemainingMinutes {
                Text("\(remaining) min remaining")
                    .font(.subheadline)
                    .foregroundStyle(Color.accentColor)

                Button("Turn Off Sleep Timer", role: .destructive) {
                    onCancelTimer()
                    onDismiss()
                }
                .buttonStyle(.bordered)
            }

            ForEach(options, id: \.self) { mins in
                Button(action: {
                    onSetTimer(mins)
                    onDismiss()
                }) {
                    HStack {
                        Text("\(mins) Minutes")
                        Spacer()
                    }
                    .padding()
                    .background(Color.secondary.opacity(0.1))
                    .clipShape(RoundedRectangle(cornerRadius: 10))
                }
                .buttonStyle(.plain)
            }
        }
        .padding(20)
        .frame(maxWidth: 340)
        .background(.ultraThickMaterial)
        .clipShape(RoundedRectangle(cornerRadius: 16))
    }
}

/// Buffer preset selection dialog sheet.
public struct BufferPresetSheet: View {
    public let currentDuration: Int
    public let bufferPercentage: Int
    public let onSelectDuration: (Int) -> Void
    public let onDismiss: () -> Void

    public init(
        currentDuration: Int,
        bufferPercentage: Int,
        onSelectDuration: @escaping (Int) -> Void,
        onDismiss: @escaping () -> Void
    ) {
        self.currentDuration = currentDuration
        self.bufferPercentage = bufferPercentage
        self.onSelectDuration = onSelectDuration
        self.onDismiss = onDismiss
    }

    public var body: some View {
        VStack(spacing: 16) {
            HStack {
                Label("Playback Buffer", systemImage: "speedometer")
                    .font(.headline)
                Spacer()
                Button(action: onDismiss) {
                    Image(systemName: "xmark.circle.fill")
                        .foregroundStyle(.secondary)
                }
                .buttonStyle(.plain)
            }

            VStack(alignment: .leading, spacing: 6) {
                HStack {
                    Text("Buffer Fill Level")
                        .font(.caption)
                        .foregroundStyle(.secondary)
                    Spacer()
                    Text("\(bufferPercentage)%")
                        .font(.caption.bold())
                }
                ProgressView(value: Double(min(max(0, bufferPercentage), 100)), total: 100.0)
            }
            .padding()
            .background(Color.secondary.opacity(0.1))
            .clipShape(RoundedRectangle(cornerRadius: 10))

            ForEach(AppSettings.bufferPresets, id: \.0) { seconds, label in
                Button(action: {
                    onSelectDuration(seconds)
                    onDismiss()
                }) {
                    HStack {
                        Text(label)
                            .font(.subheadline)
                        Spacer()
                        if currentDuration == seconds {
                            Image(systemName: "checkmark")
                                .foregroundStyle(Color.accentColor)
                        }
                    }
                    .padding()
                    .background(Color.secondary.opacity(0.1))
                    .clipShape(RoundedRectangle(cornerRadius: 10))
                }
                .buttonStyle(.plain)
            }
        }
        .padding(20)
        .frame(maxWidth: 360)
        .background(.ultraThickMaterial)
        .clipShape(RoundedRectangle(cornerRadius: 16))
    }
}
