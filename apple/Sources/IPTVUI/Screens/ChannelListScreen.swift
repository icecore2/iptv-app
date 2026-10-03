import SwiftUI
import IPTVCore

/// Main channel browser screen displaying categories, content filters, live EPG cards, and search.
public struct ChannelListScreen: View {
    @ObservedObject public var viewModel: PlaylistViewModel
    @ObservedObject public var settingsViewModel: SettingsViewModel
    public let onChannelSelected: (M3uItem, [M3uItem]) -> Void
    public let onOpenEpgGuide: () -> Void
    public let onOpenSettings: () -> Void
    public let onChangePlaylist: () -> Void

    @State private var showFilterSheet: Bool = false
    @State private var showPlaylistSwitcher: Bool = false
    @State private var showEditSheet: Bool = false
    @State private var editingPair: SavedPlaylistPair? = nil

    private let gridColumns = [
        GridItem(.adaptive(minimum: 150), spacing: 12)
    ]

    public init(
        viewModel: PlaylistViewModel,
        settingsViewModel: SettingsViewModel,
        onChannelSelected: @escaping (M3uItem, [M3uItem]) -> Void,
        onOpenEpgGuide: @escaping () -> Void,
        onOpenSettings: @escaping () -> Void,
        onChangePlaylist: @escaping () -> Void
    ) {
        self.viewModel = viewModel
        self.settingsViewModel = settingsViewModel
        self.onChannelSelected = onChannelSelected
        self.onOpenEpgGuide = onOpenEpgGuide
        self.onOpenSettings = onOpenSettings
        self.onChangePlaylist = onChangePlaylist
    }

    public var body: some View {
        NavigationStack {
            VStack(spacing: 0) {
                // Content Type Filter Bar (All / Live TV / VOD / Favorites)
                contentTypeBar

                // Horizontally scrolling category pills
                categoryPillsBar

                // Channel Grid or List
                channelContentView
            }
            .navigationTitle(viewModel.activePair?.name ?? "Channels")
            .inlineTitleMode()
            .searchable(text: $viewModel.searchQuery, prompt: "Search channels & live shows...")
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button(action: { showPlaylistSwitcher = true }) {
                        Label("Playlists", systemImage: "rectangle.stack")
                    }
                }

                ToolbarItemGroup(placement: .primaryAction) {
                    Button(action: { viewModel.toggleViewMode() }) {
                        Image(systemName: viewModel.viewMode == .list ? "square.grid.2x2" : "list.bullet")
                    }

                    Button(action: { showFilterSheet = true }) {
                        ZStack(alignment: .topTrailing) {
                            Image(systemName: "line.3.horizontal.decrease.circle")
                            if viewModel.hasActiveFilters() {
                                Circle()
                                    .fill(Color.accentColor)
                                    .frame(width: 8, height: 8)
                                    .offset(x: 2, y: -2)
                            }
                        }
                    }

                    Button(action: onOpenEpgGuide) {
                        Image(systemName: "calendar")
                    }

                    Button(action: onOpenSettings) {
                        Image(systemName: "gearshape")
                    }
                }
            }
            .sheet(isPresented: $showFilterSheet) {
                ChannelFilterSheet(
                    categories: viewModel.categories,
                    categoryCounts: viewModel.getCategoryCounts(),
                    selectedCategory: $viewModel.selectedCategory,
                    onlyWithEpg: $viewModel.onlyWithEpg,
                    onlyLiveNow: $viewModel.onlyLiveNow,
                    sortBy: $viewModel.sortBy,
                    onReset: { viewModel.resetFilters() },
                    onDismiss: { showFilterSheet = false }
                )
            }
            .sheet(isPresented: $showPlaylistSwitcher) {
                PlaylistSwitcherSheet(
                    savedPlaylists: viewModel.savedPlaylists,
                    activePairId: viewModel.activePairId,
                    onSelectPair: { pair in
                        Task { await viewModel.switchToPlaylist(pair) }
                    },
                    onAddNew: {
                        editingPair = nil
                        showEditSheet = true
                    },
                    onEditPair: { pair in
                        editingPair = pair
                        showEditSheet = true
                    },
                    onDeletePair: { pair in
                        Task { await viewModel.deletePlaylist(id: pair.id) }
                    },
                    onDismiss: { showPlaylistSwitcher = false }
                )
            }
            .sheet(isPresented: $showEditSheet) {
                PlaylistEditSheet(
                    existingPair: editingPair,
                    onSave: { name, playUrl, epgUrl in
                        Task {
                            if let existing = editingPair {
                                var updated = existing
                                updated.name = name
                                updated.playlistUrl = playUrl
                                updated.epgUrl = epgUrl
                                await viewModel.updatePlaylist(updated)
                            } else {
                                await viewModel.savePlaylist(name: name, playlistUrl: playUrl, epgUrl: epgUrl)
                            }
                        }
                    },
                    onDismiss: { showEditSheet = false }
                )
            }
        }
    }

    private var contentTypeBar: some View {
        Picker("Content", selection: $viewModel.contentType) {
            ForEach(ContentTypeFilter.allCases) { filter in
                Text(filter.label).tag(filter)
            }
        }
        .pickerStyle(.segmented)
        .padding(.horizontal)
        .padding(.vertical, 8)
    }

    private var categoryPillsBar: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 8) {
                ForEach(viewModel.categories, id: \.self) { category in
                    let isSelected = (category == viewModel.selectedCategory)
                    let count = viewModel.getCategoryCounts()[category] ?? 0

                    Button(action: { viewModel.selectCategory(category) }) {
                        Text("\(category) (\(count))")
                            .font(.caption.bold())
                            .padding(.horizontal, 12)
                            .padding(.vertical, 6)
                            .background(isSelected ? Color.accentColor : Color.secondary.opacity(0.12))
                            .foregroundStyle(isSelected ? Color.white : Color.primary)
                            .clipShape(Capsule())
                    }
                    .buttonStyle(.plain)
                }
            }
            .padding(.horizontal)
            .padding(.bottom, 8)
        }
    }

    @ViewBuilder
    private var channelContentView: some View {
        let filtered = viewModel.filteredChannels
        let rawChannels = filtered.map { $0.channel }

        if viewModel.isLoading {
            Spacer()
            ProgressView("Loading playlist...")
            Spacer()
        } else if filtered.isEmpty {
            Spacer()
            ContentUnavailableView(
                "No Channels Found",
                systemImage: "tv.slash",
                description: Text("No channels match your active search or filters.")
            )
            Button("Reset Filters") {
                viewModel.resetFilters()
            }
            .buttonStyle(.bordered)
            .padding(.top, 8)
            Spacer()
        } else {
            ScrollView {
                if viewModel.viewMode == .grid {
                    LazyVGrid(columns: gridColumns, spacing: 12) {
                        ForEach(filtered) { item in
                            ChannelCardView(
                                item: item,
                                isGrid: true,
                                showLogos: settingsViewModel.settings.showChannelLogos,
                                showEpg: settingsViewModel.settings.showEpgInList,
                                isFavorite: viewModel.isFavorite(channelId: item.channel.id),
                                onSelect: { onChannelSelected(item.channel, rawChannels) },
                                onToggleFavorite: { viewModel.toggleFavorite(channelId: item.channel.id) }
                            )
                        }
                    }
                    .padding(12)
                } else {
                    LazyVStack(spacing: 8) {
                        ForEach(filtered) { item in
                            ChannelCardView(
                                item: item,
                                isGrid: false,
                                showLogos: settingsViewModel.settings.showChannelLogos,
                                showEpg: settingsViewModel.settings.showEpgInList,
                                isFavorite: viewModel.isFavorite(channelId: item.channel.id),
                                onSelect: { onChannelSelected(item.channel, rawChannels) },
                                onToggleFavorite: { viewModel.toggleFavorite(channelId: item.channel.id) }
                            )
                        }
                    }
                    .padding(.horizontal, 12)
                    .padding(.vertical, 8)
                }
            }
            .refreshable {
                await viewModel.reloadCurrentPlaylist()
            }
        }
    }
}
