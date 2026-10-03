import SwiftUI
import IPTVCore

/// macOS 3-column desktop split navigation with sidebar, channel browser, and integrated player.
public struct MacMainSplitView: View {
    @ObservedObject public var playlistViewModel: PlaylistViewModel
    @ObservedObject public var playerViewModel: PlayerViewModel
    @ObservedObject public var settingsViewModel: SettingsViewModel

    @State private var selectedChannel: M3uItem? = nil
    @State private var showSettings: Bool = false
    @State private var showEpgSheet: Bool = false

    public init(
        playlistViewModel: PlaylistViewModel,
        playerViewModel: PlayerViewModel,
        settingsViewModel: SettingsViewModel
    ) {
        self.playlistViewModel = playlistViewModel
        self.playerViewModel = playerViewModel
        self.settingsViewModel = settingsViewModel
    }

    public var body: some View {
        NavigationSplitView {
            // Column 1: Sidebar
            sidebarView
        } content: {
            // Column 2: Channel Browser
            channelBrowserView
        } detail: {
            // Column 3: Embedded Video Player & Details
            playerDetailView
        }
        .navigationTitle(playlistViewModel.activePair?.name ?? "IPTV Player")
        .sheet(isPresented: $showSettings) {
            SettingsScreen(
                viewModel: settingsViewModel,
                onBack: { showSettings = false }
            )
            .frame(minWidth: 500, minHeight: 600)
        }
    }

    // MARK: - Sidebar

    private var sidebarView: some View {
        List {
            Section(header: Text("Playlists")) {
                ForEach(playlistViewModel.savedPlaylists) { pair in
                    let isActive = (pair.id == playlistViewModel.activePairId)
                    Button(action: {
                        Task { await playlistViewModel.switchToPlaylist(pair) }
                    }) {
                        Label(pair.name, systemImage: isActive ? "checkmark.circle.fill" : "circle")
                    }
                    .buttonStyle(.plain)
                }
            }

            Section(header: Text("Content Filter")) {
                ForEach(ContentTypeFilter.allCases) { filter in
                    Button(action: { playlistViewModel.setContentType(filter) }) {
                        HStack {
                            Text(filter.label)
                            Spacer()
                            if playlistViewModel.contentType == filter {
                                Image(systemName: "checkmark")
                                    .foregroundStyle(Color.accentColor)
                            }
                        }
                    }
                    .buttonStyle(.plain)
                }
            }

            Section(header: Text("Categories")) {
                ForEach(playlistViewModel.categories, id: \.self) { cat in
                    let count = playlistViewModel.getCategoryCounts()[cat] ?? 0
                    Button(action: { playlistViewModel.selectCategory(cat) }) {
                        HStack {
                            Text(cat)
                            Spacer()
                            Text("\(count)")
                                .font(.caption)
                                .foregroundStyle(.secondary)
                        }
                    }
                    .buttonStyle(.plain)
                }
            }
        }
        .listStyle(.sidebar)
        .toolbar {
            ToolbarItem(placement: .automatic) {
                Button(action: { showSettings = true }) {
                    Label("Settings", systemImage: "gearshape")
                }
            }
        }
    }

    // MARK: - Middle Column: Channel Browser

    private var channelBrowserView: some View {
        let filtered = playlistViewModel.filteredChannels
        let rawChannels = filtered.map { $0.channel }

        return List(selection: $selectedChannel) {
            ForEach(filtered) { item in
                ChannelCardView(
                    item: item,
                    isGrid: false,
                    showLogos: settingsViewModel.settings.showChannelLogos,
                    showEpg: settingsViewModel.settings.showEpgInList,
                    isFavorite: playlistViewModel.isFavorite(channelId: item.channel.id),
                    showMetadataBadge: settingsViewModel.settings.showInlineMetadataBadge,
                    onSelect: {
                        selectedChannel = item.channel
                        playerViewModel.playChannel(
                            channel: item.channel,
                            playlist: rawChannels,
                            matcher: playlistViewModel.epgMatcher
                        )
                    },
                    onToggleFavorite: {
                        playlistViewModel.toggleFavorite(channelId: item.channel.id)
                    },
                    onOpenMetadata: { title in
                        playlistViewModel.openMetadataDetails(title: title)
                    }
                )
                .tag(item.channel)
            }
        }
        .searchable(text: $playlistViewModel.searchQuery, prompt: "Search channels...")
    }

    // MARK: - Right Column: Player Detail & Side Inspector

    private var playerDetailView: some View {
        HStack(spacing: 0) {
            Group {
                if playerViewModel.currentChannel != nil {
                    PlayerScreen(
                        viewModel: playerViewModel,
                        settingsViewModel: settingsViewModel,
                        favoriteIds: playlistViewModel.favoriteIds,
                        onToggleFavorite: { playlistViewModel.toggleFavorite(channelId: $0) },
                        onBack: {},
                        onOpenSettings: { showSettings = true }
                    )
                } else {
                    ContentUnavailableView(
                        "No Channel Selected",
                        systemImage: "tv",
                        description: Text("Select a channel from the list to begin playback.")
                    )
                }
            }

            // Split inspector window for enriched programme details
            if let metaItem = playlistViewModel.selectedMetadataItem {
                Divider()
                ProgrammeDetailsSplitView(
                    rawTitle: metaItem.title,
                    initialMetadata: metaItem.metadata,
                    metadataRepository: playlistViewModel.metadataRepository,
                    preferredLanguage: settingsViewModel.settings.metadataLanguage,
                    onClose: { playlistViewModel.closeMetadataDetails() }
                )
                .frame(width: 320)
            }
        }
    }
}
