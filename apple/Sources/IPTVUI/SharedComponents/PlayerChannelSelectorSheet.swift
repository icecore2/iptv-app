import SwiftUI
import IPTVCore

/// Quick channel switching drawer rendered over video playback.
public struct PlayerChannelSelectorSheet: View {
    public let channels: [M3uItem]
    public let currentChannel: M3uItem?
    public let matcher: EpgMatcher?
    public let onSelectChannel: (M3uItem) -> Void
    public let onDismiss: () -> Void

    @State private var searchText: String = ""
    @State private var selectedTab: Int = 0 // 0: All, 1: Live, 2: VOD

    public init(
        channels: [M3uItem],
        currentChannel: M3uItem?,
        matcher: EpgMatcher?,
        onSelectChannel: @escaping (M3uItem) -> Void,
        onDismiss: @escaping () -> Void
    ) {
        self.channels = channels
        self.currentChannel = currentChannel
        self.matcher = matcher
        self.onSelectChannel = onSelectChannel
        self.onDismiss = onDismiss
    }

    private var filteredList: [M3uItem] {
        var list = channels
        if selectedTab == 1 {
            list = list.filter { !$0.isVod }
        } else if selectedTab == 2 {
            list = list.filter { $0.isVod }
        }

        let query = searchText.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
        if !query.isEmpty {
            list = list.filter { $0.name.lowercased().contains(query) }
        }
        return list
    }

    public var body: some View {
        NavigationStack {
            VStack(spacing: 0) {
                // Tab Picker
                Picker("Content", selection: $selectedTab) {
                    Text("All").tag(0)
                    Text("Live TV").tag(1)
                    Text("VOD").tag(2)
                }
                .pickerStyle(.segmented)
                .padding(.horizontal)
                .padding(.vertical, 8)

                // Channel List
                List(filteredList) { channel in
                    let isCurrent = (channel.streamUrl == currentChannel?.streamUrl)
                    let currentProg = matcher?.getCurrentProgramme(channel: channel)

                    Button(action: {
                        onSelectChannel(channel)
                        onDismiss()
                    }) {
                        HStack(spacing: 12) {
                            Circle()
                                .fill(isCurrent ? Color.accentColor : Color.clear)
                                .frame(width: 8, height: 8)

                            VStack(alignment: .leading, spacing: 2) {
                                Text(channel.name)
                                    .font(.headline)
                                    .foregroundStyle(isCurrent ? Color.accentColor : Color.primary)
                                    .lineLimit(1)

                                if let prog = currentProg {
                                    Text(prog.title)
                                        .font(.caption)
                                        .foregroundStyle(.secondary)
                                        .lineLimit(1)
                                } else {
                                    Text(channel.group)
                                        .font(.caption2)
                                        .foregroundStyle(.secondary)
                                }
                            }

                            Spacer()

                            if channel.isVod {
                                Text("VOD")
                                    .font(.caption2.bold())
                                    .padding(.horizontal, 6)
                                    .padding(.vertical, 2)
                                    .background(Color.orange.opacity(0.2))
                                    .foregroundStyle(.orange)
                                    .clipShape(Capsule())
                            }
                        }
                    }
                    .buttonStyle(.plain)
                    .listRowBackground(isCurrent ? Color.accentColor.opacity(0.1) : Color.clear)
                }
                .listStyle(.plain)
                .searchable(text: $searchText, prompt: "Search channels...")
            }
            .navigationTitle("Switch Channel")
            .inlineTitleMode()
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Close", action: onDismiss)
                }
            }
        }
    }
}
