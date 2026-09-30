import SwiftUI
import IPTVCore

/// Displays a channel card in either List row or Grid cell layout with EPG info and favorite toggle.
public struct ChannelCardView: View {
    public let item: ChannelWithEpg
    public let isGrid: Bool
    public let showLogos: Bool
    public let showEpg: Bool
    public let isFavorite: Bool
    public let onSelect: () -> Void
    public let onToggleFavorite: () -> Void
    public let onOpenEpg: (() -> Void)?

    public init(
        item: ChannelWithEpg,
        isGrid: Bool = false,
        showLogos: Bool = true,
        showEpg: Bool = true,
        isFavorite: Bool = false,
        onSelect: @escaping () -> Void,
        onToggleFavorite: @escaping () -> Void,
        onOpenEpg: (() -> Void)? = nil
    ) {
        self.item = item
        self.isGrid = isGrid
        self.showLogos = showLogos
        self.showEpg = showEpg
        self.isFavorite = isFavorite
        self.onSelect = onSelect
        self.onToggleFavorite = onToggleFavorite
        self.onOpenEpg = onOpenEpg
    }

    public var body: some View {
        Button(action: onSelect) {
            if isGrid {
                gridLayout
            } else {
                listLayout
            }
        }
        .buttonStyle(.plain)
    }

    private var listLayout: some View {
        HStack(spacing: 12) {
            // Channel Logo / Icon
            channelLogoView
                .frame(width: 52, height: 52)
                .background(Color.secondary.opacity(0.12))
                .clipShape(RoundedRectangle(cornerRadius: 8))

            // Channel Name & Live EPG metadata
            VStack(alignment: .leading, spacing: 4) {
                HStack(spacing: 6) {
                    Text(item.channel.name)
                        .font(.headline)
                        .lineLimit(1)

                    if item.channel.isVod {
                        Text("VOD")
                            .font(.caption2.bold())
                            .padding(.horizontal, 5)
                            .padding(.vertical, 2)
                            .background(Color.orange.opacity(0.2))
                            .foregroundStyle(.orange)
                            .clipShape(Capsule())
                    }
                }

                if showEpg, let prog = item.currentProgramme {
                    Text(prog.title)
                        .font(.subheadline)
                        .foregroundStyle(.secondary)
                        .lineLimit(1)

                    ProgressView(value: item.progress)
                        .progressViewStyle(.linear)
                        .tint(.accentColor)
                        .scaleEffect(y: 0.6)
                } else {
                    Text(item.channel.group)
                        .font(.caption)
                        .foregroundStyle(.secondary)
                        .lineLimit(1)
                }
            }

            Spacer()

            // Favorite Button
            Button(action: onToggleFavorite) {
                Image(systemName: isFavorite ? "heart.fill" : "heart")
                    .foregroundStyle(isFavorite ? .red : .secondary)
                    .font(.title3)
            }
            .buttonStyle(.borderless)
            .padding(.trailing, 4)
        }
        .padding(.horizontal, 12)
        .padding(.vertical, 8)
        .background(Color.secondary.opacity(0.06))
        .clipShape(RoundedRectangle(cornerRadius: 12))
    }

    private var gridLayout: some View {
        VStack(alignment: .leading, spacing: 8) {
            ZStack(alignment: .topTrailing) {
                channelLogoView
                    .frame(maxWidth: .infinity)
                    .frame(height: 90)
                    .background(Color.secondary.opacity(0.12))
                    .clipShape(RoundedRectangle(cornerRadius: 10))

                Button(action: onToggleFavorite) {
                    Image(systemName: isFavorite ? "heart.fill" : "heart")
                        .foregroundStyle(isFavorite ? .red : .white)
                        .padding(6)
                        .background(.ultraThinMaterial)
                        .clipShape(Circle())
                }
                .buttonStyle(.borderless)
                .padding(6)
            }

            Text(item.channel.name)
                .font(.subheadline.bold())
                .lineLimit(1)

            if showEpg, let prog = item.currentProgramme {
                Text(prog.title)
                    .font(.caption)
                    .foregroundStyle(.secondary)
                    .lineLimit(1)

                ProgressView(value: item.progress)
                    .progressViewStyle(.linear)
                    .tint(.accentColor)
                    .scaleEffect(y: 0.5)
            } else {
                Text(item.channel.group)
                    .font(.caption2)
                    .foregroundStyle(.secondary)
                    .lineLimit(1)
            }
        }
        .padding(10)
        .background(Color.secondary.opacity(0.06))
        .clipShape(RoundedRectangle(cornerRadius: 12))
    }

    @ViewBuilder
    private var channelLogoView: some View {
        if showLogos, let logo = item.channel.logoUrl, let url = URL(string: logo) {
            AsyncImage(url: url) { phase in
                switch phase {
                case .success(let image):
                    image
                        .resizable()
                        .aspectRatio(contentMode: .fit)
                        .padding(4)
                default:
                    placeholderIcon
                }
            }
        } else {
            placeholderIcon
        }
    }

    private var placeholderIcon: some View {
        Image(systemName: item.channel.isVod ? "film" : (item.channel.isRadio ? "radio" : "tv"))
            .font(.title2)
            .foregroundStyle(.secondary)
    }
}
