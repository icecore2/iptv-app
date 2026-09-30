import SwiftUI
import IPTVCore
import IPTVData

/// Initial onboarding screen where users configure IPTV M3U playlist URLs or launch the built-in demo.
public struct PlaylistInputScreen: View {
    @ObservedObject public var viewModel: PlaylistViewModel
    @ObservedObject public var settingsViewModel: SettingsViewModel
    public let onPlaylistLoaded: () -> Void
    public let onOpenSettings: () -> Void

    @State private var playlistName: String = ""
    @State private var playlistUrl: String = ""
    @State private var epgUrl: String = ""
    @State private var editingPair: SavedPlaylistPair? = nil
    @State private var showEditSheet: Bool = false

    public init(
        viewModel: PlaylistViewModel,
        settingsViewModel: SettingsViewModel,
        onPlaylistLoaded: @escaping () -> Void,
        onOpenSettings: @escaping () -> Void
    ) {
        self.viewModel = viewModel
        self.settingsViewModel = settingsViewModel
        self.onPlaylistLoaded = onPlaylistLoaded
        self.onOpenSettings = onOpenSettings
    }

    public var body: some View {
        NavigationStack {
            ScrollView {
                VStack(spacing: 24) {
                    // Welcome Header
                    VStack(spacing: 8) {
                        Image(systemName: "tv.fill")
                            .font(.system(size: 56))
                            .foregroundStyle(Color.accentColor)

                        Text("IPTV Stream & EPG Player")
                            .font(.title2.bold())

                        Text("Stream live broadcast TV, sports, movies, and catchup guides across macOS and iOS.")
                            .font(.subheadline)
                            .foregroundStyle(.secondary)
                            .multilineTextAlignment(.center)
                            .padding(.horizontal)
                    }
                    .padding(.top, 24)

                    // Error banner if any
                    if let err = viewModel.error {
                        HStack {
                            Image(systemName: "exclamationmark.triangle.fill")
                                .foregroundStyle(.red)
                            Text(err)
                                .font(.subheadline)
                                .foregroundStyle(.red)
                            Spacer()
                        }
                        .padding()
                        .background(Color.red.opacity(0.12))
                        .clipShape(RoundedRectangle(cornerRadius: 10))
                    }

                    // Quick Start Demo Card
                    VStack(spacing: 12) {
                        HStack {
                            VStack(alignment: .leading, spacing: 4) {
                                Text("Try Free Demo Channels")
                                    .font(.headline)
                                Text("Load sample streams (NASA TV, DW News, France 24, Sintel) with interactive live EPG.")
                                    .font(.caption)
                                    .foregroundStyle(.secondary)
                            }
                            Spacer()
                        }

                        Button(action: {
                            viewModel.loadSampleData()
                            onPlaylistLoaded()
                        }) {
                            HStack {
                                Image(systemName: "play.fill")
                                Text("Launch Demo Streams")
                                    .fontWeight(.semibold)
                            }
                            .frame(maxWidth: .infinity)
                            .padding(.vertical, 12)
                        }
                        .buttonStyle(.borderedProminent)
                    }
                    .padding(16)
                    .background(Color.secondary.opacity(0.08))
                    .clipShape(RoundedRectangle(cornerRadius: 14))

                    // Custom URL Input Card
                    VStack(alignment: .leading, spacing: 14) {
                        Text("Add Custom Playlist")
                            .font(.headline)

                        TextField("Profile Name (e.g. My Provider)", text: $playlistName)
                            .textFieldStyle(.roundedBorder)

                        TextField("M3U / M3U8 Playlist URL", text: $playlistUrl)
                            .textFieldStyle(.roundedBorder)
                            .autocorrectionDisabled()
                            #if canImport(UIKit)
                            .textInputAutocapitalization(.never)
                            .keyboardType(.URL)
                            #endif

                        TextField("XMLTV EPG Guide URL (Optional)", text: $epgUrl)
                            .textFieldStyle(.roundedBorder)
                            .autocorrectionDisabled()
                            #if canImport(UIKit)
                            .textInputAutocapitalization(.never)
                            .keyboardType(.URL)
                            #endif

                        Button(action: {
                            Task {
                                let name = playlistName.trimmingCharacters(in: .whitespaces).isEmpty ? "IPTV Playlist" : playlistName
                                await viewModel.saveAndSwitch(
                                    name: name,
                                    playlistUrl: playlistUrl.trimmingCharacters(in: .whitespaces),
                                    epgUrl: epgUrl.trimmingCharacters(in: .whitespaces).isEmpty ? nil : epgUrl.trimmingCharacters(in: .whitespaces)
                                )
                                onPlaylistLoaded()
                            }
                        }) {
                            if viewModel.isLoading {
                                ProgressView()
                                    .frame(maxWidth: .infinity)
                                    .padding(.vertical, 10)
                            } else {
                                Text("Save & Load Channels")
                                    .fontWeight(.semibold)
                                    .frame(maxWidth: .infinity)
                                    .padding(.vertical, 10)
                            }
                        }
                        .buttonStyle(.borderedProminent)
                        .disabled(playlistUrl.trimmingCharacters(in: .whitespaces).isEmpty || viewModel.isLoading)
                    }
                    .padding(16)
                    .background(Color.secondary.opacity(0.08))
                    .clipShape(RoundedRectangle(cornerRadius: 14))

                    // Saved Profiles Section
                    if !viewModel.savedPlaylists.isEmpty {
                        VStack(alignment: .leading, spacing: 12) {
                            Text("Saved Profiles")
                                .font(.headline)

                            ForEach(viewModel.savedPlaylists) { pair in
                                let isActive = (pair.id == viewModel.activePairId)
                                HStack {
                                    VStack(alignment: .leading, spacing: 2) {
                                        Text(pair.name)
                                            .font(.subheadline.bold())
                                        Text(pair.playlistUrl)
                                            .font(.caption2)
                                            .foregroundStyle(.secondary)
                                            .lineLimit(1)
                                    }

                                    Spacer()

                                    if isActive {
                                        Image(systemName: "checkmark.circle.fill")
                                            .foregroundStyle(Color.accentColor)
                                    }

                                    Button(action: {
                                        Task {
                                            await viewModel.switchToPlaylist(pair)
                                            onPlaylistLoaded()
                                        }
                                    }) {
                                        Text("Open")
                                            .font(.caption.bold())
                                    }
                                    .buttonStyle(.bordered)
                                    .controlSize(.small)
                                }
                                .padding(12)
                                .background(Color.secondary.opacity(0.05))
                                .clipShape(RoundedRectangle(cornerRadius: 10))
                            }
                        }
                    }
                }
                .padding(.horizontal, 20)
                .padding(.bottom, 32)
            }
            .navigationTitle("IPTV Player")
            .toolbar {
                ToolbarItem(placement: .primaryAction) {
                    Button(action: onOpenSettings) {
                        Image(systemName: "gearshape")
                    }
                }
            }
        }
    }
}
