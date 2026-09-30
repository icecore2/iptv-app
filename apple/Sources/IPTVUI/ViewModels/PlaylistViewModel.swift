import Foundation
import Combine
import IPTVCore
import IPTVData

public enum ChannelSortOrder: String, CaseIterable, Identifiable, Sendable {
    case `default` = "DEFAULT"
    case nameAsc = "NAME_ASC"
    case nameDesc = "NAME_DESC"
    case group = "GROUP"

    public var id: String { rawValue }

    public var label: String {
        switch self {
        case .default: return "Default Playlist Order"
        case .nameAsc: return "Name: A to Z"
        case .nameDesc: return "Name: Z to A"
        case .group: return "Group / Category"
        }
    }
}

public enum ContentTypeFilter: String, CaseIterable, Identifiable, Sendable {
    case all = "ALL"
    case liveTv = "LIVE_TV"
    case vod = "VOD"
    case favorites = "FAVORITES"

    public var id: String { rawValue }

    public var label: String {
        switch self {
        case .all: return "All Channels"
        case .liveTv: return "Live TV"
        case .vod: return "Movies & VOD"
        case .favorites: return "Favorites"
        }
    }
}

public enum ViewMode: String, CaseIterable, Identifiable, Sendable {
    case list = "LIST"
    case grid = "GRID"

    public var id: String { rawValue }
}

@MainActor
public final class PlaylistViewModel: ObservableObject {

    @Published public var isLoading: Bool = false
    @Published public var error: String? = nil
    @Published public var playlist: M3uPlaylist? = nil
    @Published public var currentUrl: String? = nil
    @Published public var currentEpgUrl: String? = nil
    @Published public var channels: [ChannelWithEpg] = []
    @Published public var categories: [String] = ["All"]
    @Published public var selectedCategory: String = "All"
    @Published public var searchQuery: String = ""
    @Published public var onlyWithEpg: Bool = false
    @Published public var onlyLiveNow: Bool = false
    @Published public var sortBy: ChannelSortOrder = .default
    @Published public var contentType: ContentTypeFilter = .all
    @Published public var viewMode: ViewMode = .list
    @Published public var favoriteIds: Set<String> = []
    @Published public var recentChannels: [M3uItem] = []
    @Published public var epgData: EpgData? = nil
    @Published public var epgMatcher: EpgMatcher? = nil
    @Published public var savedPlaylists: [SavedPlaylistPair] = []
    @Published public var activePairId: String? = nil

    private let playlistRepository: PlaylistRepository
    private let epgRepository: EpgRepository
    private let savedPlaylistRepository: SavedPlaylistRepository

    public init(
        playlistRepository: PlaylistRepository = PlaylistRepository(),
        epgRepository: EpgRepository = EpgRepository(),
        savedPlaylistRepository: SavedPlaylistRepository = UserDefaultsSavedPlaylistRepository()
    ) {
        self.playlistRepository = playlistRepository
        self.epgRepository = epgRepository
        self.savedPlaylistRepository = savedPlaylistRepository

        Task {
            await loadSavedPlaylists()
        }
    }

    public var activePair: SavedPlaylistPair? {
        savedPlaylists.first(where: { $0.id == activePairId })
    }

    /// Evaluates category filters, content types, EPG availability toggles, search terms, and sorting.
    public var filteredChannels: [ChannelWithEpg] {
        var list = channels

        // 1. Content Type Filter
        switch contentType {
        case .all:
            break
        case .liveTv:
            list = list.filter { !$0.channel.isVod }
        case .vod:
            list = list.filter { $0.channel.isVod }
        case .favorites:
            list = list.filter { favoriteIds.contains($0.channel.id) }
        }

        // 2. Category Filter
        if selectedCategory != "All" {
            list = list.filter { $0.channel.group.caseInsensitiveCompare(selectedCategory) == .orderedSame }
        }

        // 3. EPG toggles
        if onlyWithEpg {
            list = list.filter { $0.currentProgramme != nil || $0.nextProgramme != nil }
        }
        if onlyLiveNow {
            list = list.filter { $0.currentProgramme != nil }
        }

        // 4. Search query
        let query = searchQuery.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
        if !query.isEmpty {
            list = list.filter { item in
                item.channel.name.lowercased().contains(query) ||
                (item.currentProgramme?.title.lowercased().contains(query) == true)
            }
        }

        // 5. Sorting
        switch sortBy {
        case .default:
            return list
        case .nameAsc:
            return list.sorted(by: { $0.channel.name.localizedCaseInsensitiveCompare($1.channel.name) == .orderedAscending })
        case .nameDesc:
            return list.sorted(by: { $0.channel.name.localizedCaseInsensitiveCompare($1.channel.name) == .orderedDescending })
        case .group:
            return list.sorted {
                if $0.channel.group == $1.channel.group {
                    return $0.channel.name.localizedCaseInsensitiveCompare($1.channel.name) == .orderedAscending
                }
                return $0.channel.group.localizedCaseInsensitiveCompare($1.channel.group) == .orderedAscending
            }
        }
    }

    public func loadSavedPlaylists(autoLoadActive: Bool = false) async {
        let list = await savedPlaylistRepository.getSavedPlaylists()
        let activeId = await savedPlaylistRepository.getActivePairId()
        self.savedPlaylists = list
        self.activePairId = activeId ?? self.activePairId

        if autoLoadActive, let activeId = activeId, channels.isEmpty {
            if let pair = list.first(where: { $0.id == activeId }) {
                await switchToPlaylist(pair)
            }
        }
    }

    public func switchToPlaylist(_ pair: SavedPlaylistPair) async {
        await savedPlaylistRepository.setActivePairId(pair.id)
        self.activePairId = pair.id
        self.favoriteIds = pair.favoriteIds

        if pair.isSample {
            loadSampleData()
        } else {
            await loadPlaylist(from: pair.playlistUrl, explicitEpgUrl: pair.epgUrl)
        }
    }

    public func saveAndSwitch(name: String, playlistUrl: String, epgUrl: String?) async {
        let newPair = SavedPlaylistPair(
            name: name.trimmingCharacters(in: .whitespacesAndNewlines),
            playlistUrl: playlistUrl.trimmingCharacters(in: .whitespacesAndNewlines),
            epgUrl: epgUrl?.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty == false ? epgUrl : nil
        )
        await savedPlaylistRepository.savePlaylist(newPair)
        self.savedPlaylists = await savedPlaylistRepository.getSavedPlaylists()
        await switchToPlaylist(newPair)
    }

    public func savePlaylist(name: String, playlistUrl: String, epgUrl: String?) async {
        let newPair = SavedPlaylistPair(
            name: name.trimmingCharacters(in: .whitespacesAndNewlines),
            playlistUrl: playlistUrl.trimmingCharacters(in: .whitespacesAndNewlines),
            epgUrl: epgUrl?.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty == false ? epgUrl : nil
        )
        await savedPlaylistRepository.savePlaylist(newPair)
        self.savedPlaylists = await savedPlaylistRepository.getSavedPlaylists()
    }

    public func updatePlaylist(_ pair: SavedPlaylistPair) async {
        await savedPlaylistRepository.updatePlaylist(pair)
        self.savedPlaylists = await savedPlaylistRepository.getSavedPlaylists()

        if activePairId == pair.id {
            if pair.isSample {
                loadSampleData()
            } else if currentUrl != pair.playlistUrl || currentEpgUrl != pair.epgUrl {
                await loadPlaylist(from: pair.playlistUrl, explicitEpgUrl: pair.epgUrl)
            }
        }
    }

    public func deletePlaylist(id: String) async {
        await savedPlaylistRepository.deletePlaylist(id: id)
        let wasActive = (activePairId == id)
        self.savedPlaylists = await savedPlaylistRepository.getSavedPlaylists()

        if wasActive {
            if let first = savedPlaylists.first {
                await switchToPlaylist(first)
            } else {
                clearPlaylist()
                self.activePairId = nil
            }
        }
    }

    public func loadSampleData() {
        let samplePlaylist = SampleDataProvider.getSamplePlaylist()
        let sampleEpg = SampleDataProvider.getSampleEpg()
        let matcher = EpgMatcher(epgData: sampleEpg)
        let enriched = samplePlaylist.items.map { matcher.enrichChannel(channel: $0) }

        var cats = ["All"]
        cats.append(contentsOf: samplePlaylist.groups)

        self.isLoading = false
        self.error = nil
        self.playlist = samplePlaylist
        self.currentUrl = nil
        self.currentEpgUrl = nil
        self.channels = enriched
        self.categories = Array(NSOrderedSet(array: cats)) as! [String]
        self.selectedCategory = "All"
        self.epgData = sampleEpg
        self.epgMatcher = matcher
        self.activePairId = activePairId ?? SampleDataProvider.defaultSamplePair.id
    }

    public func clearPlaylist() {
        self.isLoading = false
        self.error = nil
        self.playlist = nil
        self.currentUrl = nil
        self.currentEpgUrl = nil
        self.channels = []
        self.categories = ["All"]
        self.selectedCategory = "All"
        self.searchQuery = ""
        self.onlyWithEpg = false
        self.onlyLiveNow = false
        self.sortBy = .default
        self.contentType = .all
        self.epgData = nil
        self.epgMatcher = nil
    }

    public func loadPlaylist(from url: String, explicitEpgUrl: String? = nil) async {
        self.isLoading = true
        self.error = nil
        self.currentUrl = url
        self.currentEpgUrl = explicitEpgUrl

        do {
            let loadedPlaylist = try await playlistRepository.loadPlaylist(from: url)
            var cats = ["All"]
            cats.append(contentsOf: loadedPlaylist.groups)

            let currentMatcher = self.epgMatcher
            let enriched = loadedPlaylist.items.map {
                currentMatcher?.enrichChannel(channel: $0) ?? ChannelWithEpg(channel: $0)
            }

            self.isLoading = false
            self.playlist = loadedPlaylist
            self.channels = enriched
            self.categories = Array(NSOrderedSet(array: cats)) as! [String]
            self.selectedCategory = "All"

            let targetEpgUrl = explicitEpgUrl ?? loadedPlaylist.epgUrl
            if let epgUrl = targetEpgUrl, !epgUrl.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
                await loadEpg(from: epgUrl)
            }
        } catch {
            self.isLoading = false
            self.error = "Failed to load playlist: \(error.localizedDescription)"
        }
    }

    public func reloadCurrentPlaylist() async {
        if let pair = activePair {
            await switchToPlaylist(pair)
            return
        }
        if let url = currentUrl {
            await loadPlaylist(from: url, explicitEpgUrl: currentEpgUrl)
        } else {
            loadSampleData()
        }
    }

    public func loadEpg(from epgUrl: String) async {
        do {
            let loadedEpg = try await epgRepository.loadEpg(from: epgUrl)
            let matcher = EpgMatcher(epgData: loadedEpg)
            let enriched = self.channels.map { matcher.enrichChannel(channel: $0.channel) }

            self.epgData = loadedEpg
            self.epgMatcher = matcher
            self.channels = enriched
        } catch {
            // Retain channels without crashing if remote EPG download fails
        }
    }

    public func selectCategory(_ category: String) {
        self.selectedCategory = category
    }

    public func updateSearchQuery(_ query: String) {
        self.searchQuery = query
    }

    public func setContentType(_ type: ContentTypeFilter) {
        self.contentType = type
    }

    public func toggleViewMode() {
        self.viewMode = (viewMode == .list) ? .grid : .list
    }

    public func toggleFavorite(channelId: String) {
        if favoriteIds.contains(channelId) {
            favoriteIds.remove(channelId)
        } else {
            favoriteIds.insert(channelId)
        }

        if let activeId = activePairId {
            let favs = favoriteIds
            Task {
                await savedPlaylistRepository.updateFavorites(pairId: activeId, favoriteIds: favs)
            }
        }
    }

    public func isFavorite(channelId: String) -> Bool {
        favoriteIds.contains(channelId)
    }

    public func addRecentChannel(_ channel: M3uItem) {
        var existing = recentChannels.filter { $0.id != channel.id }
        existing.insert(channel, at: 0)
        recentChannels = Array(existing.prefix(12))
    }

    public func setFilterOptions(
        category: String? = nil,
        onlyWithEpg: Bool? = nil,
        onlyLiveNow: Bool? = nil,
        sortBy: ChannelSortOrder? = nil
    ) {
        if let cat = category { self.selectedCategory = cat }
        if let withEpg = onlyWithEpg { self.onlyWithEpg = withEpg }
        if let liveNow = onlyLiveNow { self.onlyLiveNow = liveNow }
        if let sort = sortBy { self.sortBy = sort }
    }

    public func resetFilters() {
        self.selectedCategory = "All"
        self.onlyWithEpg = false
        self.onlyLiveNow = false
        self.sortBy = .default
        self.contentType = .all
        self.searchQuery = ""
    }

    public func hasActiveFilters() -> Bool {
        selectedCategory != "All" || onlyWithEpg || onlyLiveNow ||
        sortBy != .default || contentType != .all
    }

    public func getCategoryCounts() -> [String: Int] {
        var counts: [String: Int] = [:]
        counts["All"] = channels.count
        for item in channels {
            let grp = item.channel.group
            counts[grp, default: 0] += 1
        }
        return counts
    }

    public func getChannelSchedule(for channel: M3uItem) -> [EpgProgramme] {
        epgMatcher?.getSchedule(channel: channel) ?? []
    }
}
