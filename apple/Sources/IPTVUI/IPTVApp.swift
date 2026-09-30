import SwiftUI
import IPTVCore
import IPTVData
import IPTVPlayer

/// Main application entry point for macOS and iOS.
public struct IPTVAppRootView: View {
    @StateObject private var settingsViewModel = SettingsViewModel()
    @StateObject private var playlistViewModel = PlaylistViewModel()
    @StateObject private var playerViewModel = PlayerViewModel()

    public init() {}

    public var body: some View {
        AppNavigation(
            playlistViewModel: playlistViewModel,
            playerViewModel: playerViewModel,
            settingsViewModel: settingsViewModel
        )
        .task {
            if settingsViewModel.settings.autoLoadLastPlaylist {
                await playlistViewModel.loadSavedPlaylists(autoLoadActive: true)
            }
        }
    }
}

#if os(macOS)
public struct IPTVPlaybackCommands: Commands {
    @ObservedObject var playerViewModel: PlayerViewModel

    public init(playerViewModel: PlayerViewModel) {
        self.playerViewModel = playerViewModel
    }

    public var body: some Commands {
        CommandMenu("Playback") {
            Button("Play / Pause") {
                playerViewModel.togglePlayPause()
            }
            .keyboardShortcut(.space, modifiers: [])

            Button("Seek Forward 10s") {
                playerViewModel.seekBy(deltaSeconds: 10)
            }
            .keyboardShortcut(.rightArrow, modifiers: [.command])

            Button("Seek Backward 10s") {
                playerViewModel.seekBy(deltaSeconds: -10)
            }
            .keyboardShortcut(.leftArrow, modifiers: [.command])

            Divider()

            Button("Next Channel") {
                playerViewModel.playNext()
            }
            .keyboardShortcut(.downArrow, modifiers: [.command])

            Button("Previous Channel") {
                playerViewModel.playPrevious()
            }
            .keyboardShortcut(.upArrow, modifiers: [.command])

            Divider()

            Button("Toggle Mute") {
                playerViewModel.toggleMute()
            }
            .keyboardShortcut("m", modifiers: [.command])

            Menu("Aspect Ratio") {
                Button("Fit (Letterbox)") { playerViewModel.setAspectRatioMode(.fit) }
                    .keyboardShortcut("1", modifiers: [.command])
                Button("Zoom (Crop)") { playerViewModel.setAspectRatioMode(.zoom) }
                    .keyboardShortcut("2", modifiers: [.command])
                Button("Fill (Stretch)") { playerViewModel.setAspectRatioMode(.fill) }
                    .keyboardShortcut("3", modifiers: [.command])
            }
        }
    }
}
#endif
