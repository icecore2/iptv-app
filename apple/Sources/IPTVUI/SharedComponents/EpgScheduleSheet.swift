import SwiftUI
import IPTVCore

/// Displays full schedule guide for a selected channel with Catchup VOD trigger and Watch Live options.
public struct EpgScheduleSheet: View {
    public let channel: M3uItem
    public let schedule: [EpgProgramme]
    public let onPlayLive: () -> Void
    public let onPlayCatchup: (EpgProgramme) -> Void
    public let onDismiss: () -> Void

    public init(
        channel: M3uItem,
        schedule: [EpgProgramme],
        onPlayLive: @escaping () -> Void,
        onPlayCatchup: @escaping (EpgProgramme) -> Void,
        onDismiss: @escaping () -> Void
    ) {
        self.channel = channel
        self.schedule = schedule
        self.onPlayLive = onPlayLive
        self.onPlayCatchup = onPlayCatchup
        self.onDismiss = onDismiss
    }

    public var body: some View {
        NavigationStack {
            Group {
                if schedule.isEmpty {
                    ContentUnavailableView(
                        "No EPG Schedule",
                        systemImage: "calendar.badge.exclamationmark",
                        description: Text("No broadcast schedule is available for this channel.")
                    )
                } else {
                    List(schedule) { programme in
                        programmeRow(programme)
                    }
                    .adaptiveListStyle()
                }
            }
            .navigationTitle(channel.name)
            .inlineTitleMode()
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Done", action: onDismiss)
                }
            }
        }
    }

    private func programmeRow(_ programme: EpgProgramme) -> some View {
        let isLive = programme.isLive()
        let isPast = programme.stopEpochMillis < Int64(Date().timeIntervalSince1970 * 1000)

        return VStack(alignment: .leading, spacing: 6) {
            HStack {
                Text(formatTimeRange(start: programme.startEpochMillis, stop: programme.stopEpochMillis))
                    .font(.caption.bold())
                    .foregroundStyle(isLive ? Color.accentColor : Color.secondary)

                if isLive {
                    Text("LIVE NOW")
                        .font(.caption2.bold())
                        .padding(.horizontal, 6)
                        .padding(.vertical, 2)
                        .background(Color.red)
                        .foregroundStyle(.white)
                        .clipShape(Capsule())
                }

                Spacer()

                if isLive {
                    Button("Watch Live", action: onPlayLive)
                        .buttonStyle(.borderedProminent)
                        .controlSize(.small)
                } else if isPast && CatchupResolver.hasCatchupSupport(channel: channel) {
                    Button(action: { onPlayCatchup(programme) }) {
                        Label("Replay", systemImage: "clock.arrow.circlepath")
                    }
                    .buttonStyle(.bordered)
                    .controlSize(.small)
                }
            }

            Text(programme.title)
                .font(.headline)

            if let desc = programme.descriptionText, !desc.isEmpty {
                Text(desc)
                    .font(.subheadline)
                    .foregroundStyle(.secondary)
                    .lineLimit(2)
            }
        }
        .padding(.vertical, 4)
    }

    private func formatTimeRange(start: Int64, stop: Int64) -> String {
        let formatter = DateFormatter()
        formatter.timeStyle = .short
        let startDate = Date(timeIntervalSince1970: TimeInterval(start / 1000))
        let stopDate = Date(timeIntervalSince1970: TimeInterval(stop / 1000))
        return "\(formatter.string(from: startDate)) – \(formatter.string(from: stopDate))"
    }
}
