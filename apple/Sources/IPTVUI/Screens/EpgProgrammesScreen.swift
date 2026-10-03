import SwiftUI
import IPTVCore

/// Electronic Programme Guide (EPG) browser displaying schedules and past programme catchup.
public struct EpgProgrammesScreen: View {
    @ObservedObject public var viewModel: PlaylistViewModel
    public let onPlayProgrammeVod: (EpgProgramme, M3uItem) -> Void
    public let onPlayChannelLive: (M3uItem) -> Void
    public let onBack: () -> Void

    @State private var selectedChannelId: String? = nil

    public init(
        viewModel: PlaylistViewModel,
        onPlayProgrammeVod: @escaping (EpgProgramme, M3uItem) -> Void,
        onPlayChannelLive: @escaping (M3uItem) -> Void,
        onBack: @escaping () -> Void
    ) {
        self.viewModel = viewModel
        self.onPlayProgrammeVod = onPlayProgrammeVod
        self.onPlayChannelLive = onPlayChannelLive
        self.onBack = onBack
    }

    private var selectedChannel: M3uItem? {
        if let id = selectedChannelId {
            return viewModel.channels.first(where: { $0.channel.id == id })?.channel
        }
        return viewModel.channels.first?.channel
    }

    public var body: some View {
        NavigationStack {
            VStack(spacing: 0) {
                // Channel Selector Strip
                ScrollView(.horizontal, showsIndicators: false) {
                    HStack(spacing: 8) {
                        ForEach(viewModel.channels) { item in
                            let isSel = (item.channel.id == (selectedChannel?.id ?? ""))
                            Button(action: { selectedChannelId = item.channel.id }) {
                                Text(item.channel.name)
                                    .font(.subheadline.bold())
                                    .padding(.horizontal, 14)
                                    .padding(.vertical, 8)
                                    .background(isSel ? Color.accentColor : Color.secondary.opacity(0.12))
                                    .foregroundStyle(isSel ? Color.white : Color.primary)
                                    .clipShape(Capsule())
                            }
                            .buttonStyle(.plain)
                        }
                    }
                    .padding(.horizontal)
                    .padding(.vertical, 10)
                }
                .background(Color.secondary.opacity(0.04))

                // Schedule List for Selected Channel
                if let channel = selectedChannel {
                    let schedule = viewModel.getChannelSchedule(for: channel)
                    if schedule.isEmpty {
                        Spacer()
                        ContentUnavailableView(
                            "No Schedule Available",
                            systemImage: "calendar.badge.exclamationmark",
                            description: Text("No XMLTV EPG data was matched for \(channel.name).")
                        )
                        Spacer()
                    } else {
                        List(schedule) { prog in
                            let isLive = prog.isLive()
                            let isPast = prog.stopEpochMillis < Int64(Date().timeIntervalSince1970 * 1000)

                            VStack(alignment: .leading, spacing: 6) {
                                HStack {
                                    Text(formatTimes(prog.startEpochMillis, prog.stopEpochMillis))
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
                                        Button("Watch Live") {
                                            onPlayChannelLive(channel)
                                        }
                                        .buttonStyle(.borderedProminent)
                                        .controlSize(.small)
                                    } else if isPast && CatchupResolver.hasCatchupSupport(channel: channel) {
                                        Button("Catchup") {
                                            onPlayProgrammeVod(prog, channel)
                                        }
                                        .buttonStyle(.bordered)
                                        .controlSize(.small)
                                    }
                                }

                                Text(prog.title)
                                    .font(.headline)

                                if let desc = prog.descriptionText, !desc.isEmpty {
                                    Text(desc)
                                        .font(.subheadline)
                                        .foregroundStyle(.secondary)
                                }
                            }
                            .padding(.vertical, 6)
                        }
                        .adaptiveListStyle()
                    }
                } else {
                    Spacer()
                    Text("Select a channel to view its programme guide.")
                        .foregroundStyle(.secondary)
                    Spacer()
                }
            }
            .navigationTitle("TV Guide & Schedules")
            .inlineTitleMode()
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Back", action: onBack)
                }
            }
        }
    }

    private func formatTimes(_ start: Int64, _ stop: Int64) -> String {
        let formatter = DateFormatter()
        formatter.timeStyle = .short
        let startDate = Date(timeIntervalSince1970: TimeInterval(start / 1000))
        let stopDate = Date(timeIntervalSince1970: TimeInterval(stop / 1000))
        return "\(formatter.string(from: startDate)) – \(formatter.string(from: stopDate))"
    }
}
