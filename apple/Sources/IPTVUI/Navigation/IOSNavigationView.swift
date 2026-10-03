import SwiftUI
import IPTVCore

public enum IOSRoute: Hashable {
    case input
    case channels
    case epg
    case player
    case settings
}

/// Navigation router for iOS and iPadOS.
public struct IOSNavigationView: View {
    @ObservedObject public var playlistViewModel: PlaylistViewModel
    @ObservedObject public var playerViewModel: PlayerViewModel
    @ObservedObject public var settingsViewModel: SettingsViewModel

    @State private var navigationPath: [IOSRoute] = []

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
        NavigationStack(path: $navigationPath) {
            Group {
                if playlistViewModel.channels.isEmpty {
                    PlaylistInputScreen(
                        viewModel: playlistViewModel,
                        settingsViewModel: settingsViewModel,
                        onPlaylistLoaded: {
                            navigationPath = [.channels]
                        },
                        onOpenSettings: {
                            navigationPath.append(.settings)
                        }
                    )
                } else {
                    channelListView
                }
            }
            .navigationDestination(for: IOSRoute.self) { route in
                switch route {
                case .input:
                    PlaylistInputScreen(
                        viewModel: playlistViewModel,
                        settingsViewModel: settingsViewModel,
                        onPlaylistLoaded: {
                            navigationPath = [.channels]
                        },
                        onOpenSettings: {
                            navigationPath.append(.settings)
                        }
                    )
                case .channels:
                    channelListView
                case .epg:
                    EpgProgrammesScreen(
                        viewModel: playlistViewModel,
                        onPlayProgrammeVod: { prog, chan in
                            let raw = playlistViewModel.channels.map { $0.channel }
                            playerViewModel.playProgrammeVod(
                                programme: prog,
                                channel: chan,
                                playlist: raw,
                                matcher: playlistViewModel.epgMatcher
                            )
                            navigationPath.append(.player)
                        },
                        onPlayChannelLive: { chan in
                            let raw = playlistViewModel.channels.map { $0.channel }
                            playerViewModel.playChannel(
                                channel: chan,
                                playlist: raw,
                                matcher: playlistViewModel.epgMatcher
                            )
                            navigationPath.append(.player)
                        },
                        onBack: {
                            navigationPath.removeLast()
                        }
                    )
                case .player:
                    PlayerScreen(
                        viewModel: playerViewModel,
                        settingsViewModel: settingsViewModel,
                        favoriteIds: playlistViewModel.favoriteIds,
                        onToggleFavorite: { playlistViewModel.toggleFavorite(channelId: $0) },
                        onBack: {
                            navigationPath.removeLast()
                        },
                        onOpenSettings: {
                            navigationPath.append(.settings)
                        }
                    )
                    #if os(iOS)
                    .navigationBarBackButtonHidden(true)
                    #endif
                case .settings:
                    SettingsScreen(
                        viewModel: settingsViewModel,
                        onBack: {
                            navigationPath.removeLast()
                        }
                    )
                }
            }
        }
    }

    private var channelListView: some View {
        ChannelListScreen(
            viewModel: playlistViewModel,
            settingsViewModel: settingsViewModel,
            onChannelSelected: { channel, allChannels in
                playerViewModel.playChannel(
                    channel: channel,
                    playlist: allChannels,
                    matcher: playlistViewModel.epgMatcher
                )
                navigationPath.append(.player)
            },
            onOpenEpgGuide: {
                navigationPath.append(.epg)
            },
            onOpenSettings: {
                navigationPath.append(.settings)
            },
            onChangePlaylist: {
                playlistViewModel.clearPlaylist()
                navigationPath = [.input]
            }
        )
    }
}
