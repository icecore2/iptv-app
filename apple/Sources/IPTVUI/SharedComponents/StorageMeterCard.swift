import SwiftUI

/// Visual disk storage gauge displaying media buffer usage, device free space, and cache clear action.
public struct StorageMeterCard: View {
    public let usedBytes: Int64
    public let freeBytes: Int64
    public let limitMb: Int
    public let onClearCache: () -> Void

    public init(
        usedBytes: Int64,
        freeBytes: Int64,
        limitMb: Int,
        onClearCache: @escaping () -> Void
    ) {
        self.usedBytes = usedBytes
        self.freeBytes = freeBytes
        self.limitMb = limitMb
        self.onClearCache = onClearCache
    }

    private var limitBytes: Int64 {
        Int64(limitMb) * 1024 * 1024
    }

    private var usedRatio: Double {
        guard limitBytes > 0 else { return 0 }
        return min(1.0, max(0.0, Double(usedBytes) / Double(limitBytes)))
    }

    public var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack {
                Label("Media Buffer & Time-Shift Disk Cache", systemImage: "internaldrive")
                    .font(.headline)
                Spacer()
                Button(role: .destructive, action: onClearCache) {
                    Text("Clear Cache")
                        .font(.caption.bold())
                }
                .buttonStyle(.bordered)
                .controlSize(.small)
            }

            ProgressView(value: usedRatio)
                .tint(usedRatio > 0.85 ? .red : .accentColor)

            HStack {
                Text("Used: \(formatBytes(usedBytes)) / \(limitMb) MB Limit")
                    .font(.caption)
                    .foregroundStyle(.secondary)
                Spacer()
                Text("Device Free: \(formatBytes(freeBytes))")
                    .font(.caption)
                    .foregroundStyle(.secondary)
            }
        }
        .padding(14)
        .background(Color.secondary.opacity(0.08))
        .clipShape(RoundedRectangle(cornerRadius: 12))
    }

    private func formatBytes(_ bytes: Int64) -> String {
        let formatter = ByteCountFormatter()
        formatter.countStyle = .file
        return formatter.string(fromByteCount: bytes)
    }
}
