import SwiftUI
import IPTVPlayer

#if canImport(UIKit)
import UIKit
#elseif canImport(AppKit)
import AppKit
#endif

/// Modal dialog displaying stream diagnostics, codec info, bitrate, and resolution.
public struct StreamInfoDialog: View {
    public let streamInfo: StreamInfo
    public let onDismiss: () -> Void

    public init(streamInfo: StreamInfo, onDismiss: @escaping () -> Void) {
        self.streamInfo = streamInfo
        self.onDismiss = onDismiss
    }

    public var body: some View {
        VStack(alignment: .leading, spacing: 16) {
            HStack {
                Label("Stream Diagnostics", systemImage: "info.circle.fill")
                    .font(.headline)
                Spacer()
                Button(action: onDismiss) {
                    Image(systemName: "xmark.circle.fill")
                        .font(.title3)
                        .foregroundStyle(.secondary)
                }
                .buttonStyle(.plain)
            }

            Divider()

            VStack(spacing: 10) {
                diagnosticRow(label: "Resolution", value: streamInfo.resolution)
                diagnosticRow(label: "Bitrate", value: streamInfo.bitrate)
                diagnosticRow(label: "Video Codec", value: streamInfo.videoCodec)
                diagnosticRow(label: "Audio Codec", value: streamInfo.audioCodec)
                diagnosticRow(label: "Frame Rate", value: streamInfo.frameRate)
                diagnosticRow(label: "Format", value: streamInfo.streamFormat)
                diagnosticRow(label: "Buffer Health", value: "\(streamInfo.bufferPercentage)%")
            }

            VStack(alignment: .leading, spacing: 4) {
                Text("Stream Source URL")
                    .font(.caption)
                    .foregroundStyle(.secondary)

                HStack {
                    Text(streamInfo.streamUrl)
                        .font(.caption2.monospaced())
                        .lineLimit(2)
                        .foregroundStyle(.primary)

                    Spacer()

                    Button(action: copyUrl) {
                        Image(systemName: "doc.on.doc")
                            .font(.caption)
                    }
                    .buttonStyle(.borderless)
                }
                .padding(8)
                .background(Color.secondary.opacity(0.1))
                .clipShape(RoundedRectangle(cornerRadius: 6))
            }
        }
        .padding(20)
        .frame(maxWidth: 420)
        .background(.ultraThickMaterial)
        .clipShape(RoundedRectangle(cornerRadius: 16))
        .shadow(radius: 10)
    }

    private func diagnosticRow(label: String, value: String) -> some View {
        HStack {
            Text(label)
                .font(.subheadline)
                .foregroundStyle(.secondary)
            Spacer()
            Text(value)
                .font(.subheadline.bold())
                .foregroundStyle(.primary)
        }
    }

    private func copyUrl() {
        #if canImport(UIKit)
        UIPasteboard.general.string = streamInfo.streamUrl
        #elseif canImport(AppKit)
        NSPasteboard.general.clearContents()
        NSPasteboard.general.setString(streamInfo.streamUrl, forType: .string)
        #endif
    }
}
