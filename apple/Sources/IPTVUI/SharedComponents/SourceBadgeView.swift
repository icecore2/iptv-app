import SwiftUI
import IPTVCore

/// Inline badge component displayed alongside programme or movie titles.
/// Tapping the badge opens the enriched metadata split window / sheet.
public struct SourceBadgeView: View {
    public let source: MetadataSource
    public let rating: Float?
    public let onTap: () -> Void

    public init(
        source: MetadataSource = .auto,
        rating: Float? = nil,
        onTap: @escaping () -> Void
    ) {
        self.source = source
        self.rating = rating
        self.onTap = onTap
    }

    public var body: some View {
        Button(action: onTap) {
            HStack(spacing: 4) {
                // Source label
                Text(source.badgeLabel)
                    .font(.system(size: 9, weight: .bold))
                    .foregroundColor(textColor)
                    .padding(.horizontal, 4)
                    .padding(.vertical, 2)
                    .background(sourceColor.opacity(0.2))
                    .cornerRadius(3)

                // Optional star rating
                if let r = rating {
                    HStack(spacing: 2) {
                        Image(systemName: "star.fill")
                            .font(.system(size: 8))
                            .foregroundColor(.yellow)
                        Text(String(format: "%.1f", r))
                            .font(.system(size: 9, weight: .semibold))
                            .foregroundColor(.primary)
                    }
                }
            }
            .padding(.horizontal, 5)
            .padding(.vertical, 2)
            .background(Color.secondary.opacity(0.15))
            .overlay(
                RoundedRectangle(cornerRadius: 4)
                    .stroke(sourceColor.opacity(0.4), lineWidth: 1)
            )
            .cornerRadius(4)
        }
        .buttonStyle(.plain)
    }

    private var sourceColor: Color {
        switch source {
        case .imdb:
            return .yellow
        case .trakt:
            return .red
        case .sratim:
            return .blue
        case .tvdb:
            return .green
        case .auto:
            return .purple
        }
    }

    private var textColor: Color {
        switch source {
        case .imdb:
            return .yellow
        case .trakt:
            return .red
        case .sratim:
            return .blue
        case .tvdb:
            return .green
        case .auto:
            return .purple
        }
    }
}
