import SwiftUI
import IPTVCore

/// Bottom sheet drawer to switch active playlist profiles or trigger Add/Edit/Delete actions.
public struct PlaylistSwitcherSheet: View {
    public let savedPlaylists: [SavedPlaylistPair]
    public let activePairId: String?
    public let onSelectPair: (SavedPlaylistPair) -> Void
    public let onAddNew: () -> Void
    public let onEditPair: (SavedPlaylistPair) -> Void
    public let onDeletePair: (SavedPlaylistPair) -> Void
    public let onDismiss: () -> Void

    public init(
        savedPlaylists: [SavedPlaylistPair],
        activePairId: String?,
        onSelectPair: @escaping (SavedPlaylistPair) -> Void,
        onAddNew: @escaping () -> Void,
        onEditPair: @escaping (SavedPlaylistPair) -> Void,
        onDeletePair: @escaping (SavedPlaylistPair) -> Void,
        onDismiss: @escaping () -> Void
    ) {
        self.savedPlaylists = savedPlaylists
        self.activePairId = activePairId
        self.onSelectPair = onSelectPair
        self.onAddNew = onAddNew
        self.onEditPair = onEditPair
        self.onDeletePair = onDeletePair
        self.onDismiss = onDismiss
    }

    public var body: some View {
        NavigationStack {
            List {
                Section(header: Text("Configured Profiles")) {
                    ForEach(savedPlaylists) { pair in
                        let isActive = (pair.id == activePairId)
                        HStack(spacing: 12) {
                            Image(systemName: isActive ? "checkmark.circle.fill" : "circle")
                                .foregroundStyle(isActive ? Color.accentColor : Color.secondary)
                                .font(.title3)

                            VStack(alignment: .leading, spacing: 2) {
                                HStack {
                                    Text(pair.name)
                                        .font(.headline)
                                    if pair.isSample {
                                        Text("DEMO")
                                            .font(.caption2.bold())
                                            .padding(.horizontal, 6)
                                            .padding(.vertical, 2)
                                            .background(Color.blue.opacity(0.2))
                                            .foregroundStyle(.blue)
                                            .clipShape(Capsule())
                                    }
                                }

                                Text(pair.playlistUrl)
                                    .font(.caption2)
                                    .foregroundStyle(.secondary)
                                    .lineLimit(1)
                            }

                            Spacer()

                            if !pair.isSample {
                                Button(action: { onEditPair(pair) }) {
                                    Image(systemName: "pencil")
                                        .foregroundStyle(.secondary)
                                }
                                .buttonStyle(.borderless)

                                Button(role: .destructive, action: { onDeletePair(pair) }) {
                                    Image(systemName: "trash")
                                        .foregroundStyle(.red)
                                }
                                .buttonStyle(.borderless)
                            }
                        }
                        .contentShape(Rectangle())
                        .onTapGesture {
                            onSelectPair(pair)
                            onDismiss()
                        }
                    }
                }
            }
            .listStyle(.insetGrouped)
            .navigationTitle("Switch Playlist")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Done", action: onDismiss)
                }
                ToolbarItem(placement: .primaryAction) {
                    Button(action: onAddNew) {
                        Label("Add", systemImage: "plus")
                    }
                }
            }
        }
    }
}
