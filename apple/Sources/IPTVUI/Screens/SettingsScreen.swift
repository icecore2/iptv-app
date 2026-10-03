import SwiftUI
import IPTVCore
import IPTVData

/// Preferences and configuration screen matching Android SettingsScreen.
public struct SettingsScreen: View {
    @ObservedObject public var viewModel: SettingsViewModel
    public let onBack: () -> Void

    public init(viewModel: SettingsViewModel, onBack: @escaping () -> Void) {
        self.viewModel = viewModel
        self.onBack = onBack
    }

    public var body: some View {
        NavigationStack {
            Form {
                // Section 1: General & Channel Guide
                Section(header: Text("General & Channel Guide")) {
                    Toggle("Show Channel Logos", isOn: Binding(
                        get: { viewModel.settings.showChannelLogos },
                        set: { viewModel.toggleChannelLogos($0) }
                    ))

                    Toggle("Show EPG in Channel List", isOn: Binding(
                        get: { viewModel.settings.showEpgInList },
                        set: { viewModel.toggleShowEpgInList($0) }
                    ))

                    Toggle("Enable Pagination", isOn: Binding(
                        get: { viewModel.settings.enablePagination },
                        set: { viewModel.togglePagination($0) }
                    ))

                    if viewModel.settings.enablePagination {
                        Picker("Page Size", selection: Binding(
                            get: { viewModel.settings.pageSize },
                            set: { viewModel.setPageSize($0) }
                        )) {
                            ForEach(AppSettings.pageSizeOptions, id: \.self) { size in
                                Text("\(size) channels").tag(size)
                            }
                        }
                    }

                    Toggle("Auto-Resume Last Playlist on Startup", isOn: Binding(
                        get: { viewModel.settings.autoLoadLastPlaylist },
                        set: { viewModel.toggleAutoLoadLastPlaylist($0) }
                    ))
                }

                // Section 2: Player & Streaming
                Section(header: Text("Player & Streaming Engine")) {
                    Picker("Buffer Duration Preset", selection: Binding(
                        get: { viewModel.settings.bufferDurationSeconds },
                        set: { viewModel.setBufferDuration($0) }
                    )) {
                        ForEach(AppSettings.bufferPresets, id: \.0) { sec, label in
                            Text(label).tag(sec)
                        }
                    }

                    Picker("Buffer Disk Storage Limit", selection: Binding(
                        get: { viewModel.settings.bufferStorageLimitMb },
                        set: { viewModel.setBufferStorageLimit($0) }
                    )) {
                        ForEach(AppSettings.bufferStoragePresets, id: \.0) { mb, label in
                            Text(label).tag(mb)
                        }
                    }

                    Toggle("Keep Screen Awake During Playback", isOn: Binding(
                        get: { viewModel.settings.keepScreenOn },
                        set: { viewModel.toggleKeepScreenOn($0) }
                    ))

                    Toggle("Fast Channel Switching", isOn: Binding(
                        get: { viewModel.settings.fastChannelSwitching },
                        set: { viewModel.toggleFastChannelSwitching($0) }
                    ))

                    Toggle("Hardware Video Acceleration (Metal)", isOn: Binding(
                        get: { viewModel.settings.hardwareAcceleration },
                        set: { viewModel.toggleHardwareAcceleration($0) }
                    ))

                    Picker("Default Aspect Ratio", selection: Binding(
                        get: { viewModel.settings.defaultAspectRatio },
                        set: { viewModel.setDefaultAspectRatio($0) }
                    )) {
                        ForEach(AspectRatioMode.allCases, id: \.self) { mode in
                            Text(mode.displayName).tag(mode)
                        }
                    }

                    Toggle("Real-Time Stream Diagnostics Overlay", isOn: Binding(
                        get: { viewModel.settings.showStreamInfoOverlay },
                        set: { viewModel.toggleStreamInfoOverlay($0) }
                    ))
                }

                // Section 3: Programme Metadata & EPG Enrichment
                Section(header: Text("Programme Metadata & EPG Enrichment")) {
                    Toggle("Show Inline Metadata Badge", isOn: Binding(
                        get: { viewModel.settings.showInlineMetadataBadge },
                        set: { viewModel.toggleShowInlineMetadataBadge($0) }
                    ))

                    Picker("Preferred Source", selection: Binding(
                        get: { viewModel.settings.preferredMetadataSource },
                        set: { viewModel.setPreferredMetadataSource($0) }
                    )) {
                        ForEach(MetadataSource.allCases) { src in
                            Text(src.displayName).tag(src)
                        }
                    }

                    Picker("Metadata Language", selection: Binding(
                        get: { viewModel.settings.metadataLanguage },
                        set: { viewModel.setMetadataLanguage($0) }
                    )) {
                        ForEach(AppSettings.metadataLanguageOptions, id: \.0) { code, name in
                            Text(name).tag(code)
                        }
                    }

                    HStack {
                        Text("Trakt Client ID")
                        Spacer()
                        TextField("Default / Custom", text: Binding(
                            get: { viewModel.settings.traktClientId },
                            set: { viewModel.setTraktClientId($0) }
                        ))
                        .multilineTextAlignment(.trailing)
                        .autocorrectionDisabled()
                        #if canImport(UIKit)
                        .textInputAutocapitalization(.never)
                        #endif
                    }

                    HStack {
                        Text("TheTVDB API Key")
                        Spacer()
                        TextField("Default / Custom", text: Binding(
                            get: { viewModel.settings.tvdbApiKey },
                            set: { viewModel.setTvdbApiKey($0) }
                        ))
                        .multilineTextAlignment(.trailing)
                        .autocorrectionDisabled()
                        #if canImport(UIKit)
                        .textInputAutocapitalization(.never)
                        #endif
                    }
                }

                // Section 4: Buffer Disk Cache & Storage Meter
                Section(header: Text("Disk Cache & Storage")) {
                    StorageMeterCard(
                        usedBytes: viewModel.usedStorageBytes,
                        freeBytes: viewModel.freeStorageBytes,
                        limitMb: viewModel.settings.bufferStorageLimitMb,
                        onClearCache: { viewModel.clearBufferCache() }
                    )
                }

                // Section 4: Defaults
                Section {
                    Button("Reset All Settings to Defaults", role: .destructive) {
                        Task { await viewModel.resetToDefaults() }
                    }
                }
            }
            .navigationTitle("Settings")
            .inlineTitleMode()
            .toolbar {
                ToolbarItem(placement: .confirmationAction) {
                    Button("Done", action: onBack)
                }
            }
            .onAppear {
                viewModel.refreshBufferStorage()
            }
        }
    }
}
