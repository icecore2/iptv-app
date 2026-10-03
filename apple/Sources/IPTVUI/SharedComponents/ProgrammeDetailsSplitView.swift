import SwiftUI
import IPTVCore
import IPTVData

/// Split window / inspector pane displaying rich enriched programme/movie metadata.
/// Can be used as a side panel in Mac/iPad split views or as a sheet on iPhone.
public struct ProgrammeDetailsSplitView: View {
    public let rawTitle: String
    public let initialMetadata: ProgrammeMetadata?
    public let metadataRepository: ProgrammeMetadataRepository
    public let preferredLanguage: String
    public let onClose: () -> Void

    @State private var allSources: [MetadataSource: ProgrammeMetadata] = [:]
    @State private var selectedSource: MetadataSource = .imdb
    @State private var isLoading: Bool = false
    @Environment(\.openURL) private var openURL

    public init(
        rawTitle: String,
        initialMetadata: ProgrammeMetadata? = nil,
        metadataRepository: ProgrammeMetadataRepository,
        preferredLanguage: String = "en",
        onClose: @escaping () -> Void
    ) {
        self.rawTitle = rawTitle
        self.initialMetadata = initialMetadata
        self.metadataRepository = metadataRepository
        self.preferredLanguage = preferredLanguage
        self.onClose = onClose
    }

    public var body: some View {
        VStack(spacing: 0) {
            headerBar
            Divider()

            sourceSelectorBar
            Divider()

            ScrollView {
                VStack(alignment: .leading, spacing: 16) {
                    if isLoading && currentMetadata == nil {
                        loadingView
                    } else if let meta = currentMetadata {
                        metadataContentView(meta)
                    } else {
                        emptyStateView
                    }
                }
                .padding()
            }
        }
        .background(Color.secondary.opacity(0.05))
        .task(id: rawTitle) {
            await loadAllSources()
        }
    }

    private var currentMetadata: ProgrammeMetadata? {
        allSources[selectedSource] ?? (initialMetadata?.source == selectedSource ? initialMetadata : nil)
    }

    // MARK: - Header Bar

    private var headerBar: some View {
        HStack {
            VStack(alignment: .leading, spacing: 2) {
                Text("Programme Details")
                    .font(.caption)
                    .foregroundColor(.secondary)
                    .textCase(.uppercase)
                Text(rawTitle)
                    .font(.headline)
                    .lineLimit(1)
            }
            Spacer()
            Button(action: onClose) {
                Image(systemName: "xmark.circle.fill")
                    .font(.title3)
                    .foregroundColor(.secondary)
            }
            .buttonStyle(.plain)
        }
        .padding(.horizontal)
        .padding(.vertical, 12)
    }

    // MARK: - Source Selector Bar

    private var sourceSelectorBar: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 8) {
                ForEach([MetadataSource.imdb, .trakt, .sratim, .tvdb]) { src in
                    let hasData = allSources[src] != nil || initialMetadata?.source == src
                    Button(action: { selectedSource = src }) {
                        HStack(spacing: 4) {
                            Text(src.badgeLabel)
                                .font(.system(size: 11, weight: .bold))
                            if hasData {
                                Circle()
                                    .fill(Color.green)
                                    .frame(width: 5, height: 5)
                            }
                        }
                        .padding(.horizontal, 10)
                        .padding(.vertical, 6)
                        .background(selectedSource == src ? sourceColor(for: src).opacity(0.2) : Color.secondary.opacity(0.1))
                        .foregroundColor(selectedSource == src ? sourceColor(for: src) : .primary)
                        .overlay(
                            RoundedRectangle(cornerRadius: 6)
                                .stroke(selectedSource == src ? sourceColor(for: src) : Color.clear, lineWidth: 1.5)
                        )
                        .cornerRadius(6)
                    }
                    .buttonStyle(.plain)
                }
            }
            .padding(.horizontal)
            .padding(.vertical, 8)
        }
    }

    // MARK: - Main Content View

    @ViewBuilder
    private func metadataContentView(_ meta: ProgrammeMetadata) -> some View {
        HStack(alignment: .top, spacing: 14) {
            // Poster
            if let posterUrl = meta.posterUrl, let url = URL(string: posterUrl) {
                AsyncImage(url: url) { phase in
                    switch phase {
                    case .success(let image):
                        image
                            .resizable()
                            .aspectRatio(contentMode: .fill)
                    case .failure:
                        posterPlaceholder
                    case .empty:
                        ProgressView()
                            .frame(maxWidth: .infinity, maxHeight: .infinity)
                    @unknown default:
                        posterPlaceholder
                    }
                }
                .frame(width: 100, height: 148)
                .background(Color.secondary.opacity(0.1))
                .clipShape(RoundedRectangle(cornerRadius: 8))
                .shadow(radius: 3)
            } else {
                posterPlaceholder
                    .frame(width: 100, height: 148)
            }

            // Key info
            VStack(alignment: .leading, spacing: 6) {
                Text(meta.title)
                    .font(.title3.bold())

                if let orig = meta.originalTitle, orig != meta.title {
                    Text(orig)
                        .font(.subheadline)
                        .foregroundColor(.secondary)
                }

                HStack(spacing: 8) {
                    if let y = meta.year {
                        Text(y)
                            .font(.caption)
                            .padding(.horizontal, 6)
                            .padding(.vertical, 2)
                            .background(Color.secondary.opacity(0.15))
                            .cornerRadius(4)
                    }

                    if let rating = meta.rating {
                        HStack(spacing: 3) {
                            Image(systemName: "star.fill")
                                .font(.caption2)
                                .foregroundColor(.yellow)
                            Text(String(format: "%.1f", rating))
                                .font(.caption.bold())
                        }
                        .padding(.horizontal, 6)
                        .padding(.vertical, 2)
                        .background(Color.yellow.opacity(0.2))
                        .foregroundColor(.yellow)
                        .cornerRadius(4)
                    }
                }

                // Play Trailer Button
                if let tr = meta.trailerUrl, let trUrl = URL(string: tr) {
                    Button(action: { openURL(trUrl) }) {
                        Label("Play Trailer", systemImage: "play.circle.fill")
                            .font(.caption.bold())
                            .foregroundColor(.white)
                            .padding(.horizontal, 10)
                            .padding(.vertical, 6)
                            .background(Color.red)
                            .cornerRadius(6)
                    }
                    .buttonStyle(.plain)
                    .padding(.top, 4)
                }
            }
        }

        // Genres
        if !meta.genres.isEmpty {
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 6) {
                    ForEach(meta.genres, id: \.self) { genre in
                        Text(genre)
                            .font(.caption2.bold())
                            .padding(.horizontal, 8)
                            .padding(.vertical, 4)
                            .background(Color.secondary.opacity(0.12))
                            .cornerRadius(12)
                    }
                }
            }
        }

        // Overview / Synopsis
        if let overview = meta.overview, !overview.isEmpty {
            VStack(alignment: .leading, spacing: 4) {
                Text("SYNOPSIS")
                    .font(.caption2.bold())
                    .foregroundColor(.secondary)
                Text(overview)
                    .font(.body)
                    .lineSpacing(3)
            }
        }

        // Director & Cast
        if !meta.director.isEmpty {
            VStack(alignment: .leading, spacing: 2) {
                Text("DIRECTOR")
                    .font(.caption2.bold())
                    .foregroundColor(.secondary)
                Text(meta.director.joined(separator: ", "))
                    .font(.callout)
            }
        }

        if !meta.cast.isEmpty {
            VStack(alignment: .leading, spacing: 4) {
                Text("CAST")
                    .font(.caption2.bold())
                    .foregroundColor(.secondary)
                Text(meta.cast.joined(separator: ", "))
                    .font(.callout)
                    .foregroundColor(.secondary)
            }
        }
    }

    private var posterPlaceholder: some View {
        RoundedRectangle(cornerRadius: 8)
            .fill(Color.secondary.opacity(0.12))
            .overlay(
                Image(systemName: "film")
                    .font(.largeTitle)
                    .foregroundColor(.secondary.opacity(0.5))
            )
    }

    private var loadingView: some View {
        VStack(spacing: 12) {
            ProgressView()
            Text("Fetching metadata from \(selectedSource.displayName)...")
                .font(.caption)
                .foregroundColor(.secondary)
        }
        .frame(maxWidth: .infinity)
        .padding(.vertical, 40)
    }

    private var emptyStateView: some View {
        VStack(spacing: 10) {
            Image(systemName: "questionmark.circle")
                .font(.system(size: 32))
                .foregroundColor(.secondary)
            Text("No details found on \(selectedSource.displayName)")
                .font(.callout)
                .foregroundColor(.secondary)
        }
        .frame(maxWidth: .infinity)
        .padding(.vertical, 40)
    }

    private func loadAllSources() async {
        if let initial = initialMetadata {
            allSources[initial.source] = initial
            selectedSource = initial.source
        }
        isLoading = true
        let resolved = await metadataRepository.resolveAllSources(rawTitle: rawTitle, language: preferredLanguage)
        allSources.merge(resolved) { _, new in new }
        isLoading = false

        if allSources[selectedSource] == nil {
            if let firstFound = allSources.keys.first {
                selectedSource = firstFound
            }
        }
    }

    private func sourceColor(for src: MetadataSource) -> Color {
        switch src {
        case .imdb: return .yellow
        case .trakt: return .red
        case .sratim: return .blue
        case .tvdb: return .green
        case .auto: return .purple
        }
    }
}
