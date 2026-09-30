import SwiftUI

/// Universal cross-platform navigation container routing between macOS desktop and iOS mobile interfaces.
public struct AppNavigation: View {
    @ObservedObject public var playlistViewModel: PlaylistViewModel
    @ObservedObject public var playerViewModel: PlayerViewModel
    @ObservedObject public var settingsViewModel: SettingsViewModel

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
        #if os(macOS)
        MacMainSplitView(
            playlistViewModel: playlistViewModel,
            playerViewModel: playerViewModel,
            settingsViewModel: settingsViewModel
        )
        #else
        IOSNavigationView(
            playlistViewModel: playlistViewModel,
            playerViewModel: playerViewModel,
            settingsViewModel: settingsViewModel
        )
        #endif
    }
}
