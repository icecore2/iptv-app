import SwiftUI
import IPTVCore

/// Modal sheet for creating or updating a saved IPTV playlist and EPG configuration.
public struct PlaylistEditSheet: View {
    public let existingPair: SavedPlaylistPair?
    public let onSave: (String, String, String?) -> Void
    public let onDismiss: () -> Void

    @State private var name: String = ""
    @State private var playlistUrl: String = ""
    @State private var epgUrl: String = ""

    public init(
        existingPair: SavedPlaylistPair? = nil,
        onSave: @escaping (String, String, String?) -> Void,
        onDismiss: @escaping () -> Void
    ) {
        self.existingPair = existingPair
        self.onSave = onSave
        self.onDismiss = onDismiss
        _name = State(initialValue: existingPair?.name ?? "")
        _playlistUrl = State(initialValue: existingPair?.playlistUrl ?? "")
        _epgUrl = State(initialValue: existingPair?.epgUrl ?? "")
    }

    private var isValid: Bool {
        !name.trimmingCharacters(in: .whitespaces).isEmpty &&
        !playlistUrl.trimmingCharacters(in: .whitespaces).isEmpty
    }

    public var body: some View {
        NavigationStack {
            Form {
                Section(header: Text("Playlist Profile")) {
                    TextField("Profile Name (e.g. Home Cable)", text: $name)
                    TextField("M3U / M3U8 Playlist URL", text: $playlistUrl)
                        .autocorrectionDisabled()
                        #if canImport(UIKit)
                        .textInputAutocapitalization(.never)
                        .keyboardType(.URL)
                        #endif
                }

                Section(header: Text("EPG TV Guide (Optional)")) {
                    TextField("XMLTV URL (.xml or .xml.gz)", text: $epgUrl)
                        .autocorrectionDisabled()
                        #if canImport(UIKit)
                        .textInputAutocapitalization(.never)
                        .keyboardType(.URL)
                        #endif
                }
            }
            .navigationTitle(existingPair != nil ? "Edit Playlist" : "Add Playlist")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancel", action: onDismiss)
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Save") {
                        let epgClean = epgUrl.trimmingCharacters(in: .whitespaces).isEmpty ? nil : epgUrl.trimmingCharacters(in: .whitespaces)
                        onSave(name.trimmingCharacters(in: .whitespaces), playlistUrl.trimmingCharacters(in: .whitespaces), epgClean)
                        onDismiss()
                    }
                    .disabled(!isValid)
                }
            }
        }
    }
}
