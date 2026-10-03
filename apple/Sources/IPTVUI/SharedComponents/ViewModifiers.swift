import SwiftUI

extension View {
    /// Applies inline navigation title display mode on iOS/tvOS while safely falling back on macOS.
    @ViewBuilder
    public func inlineTitleMode() -> some View {
        #if os(iOS) || os(tvOS) || os(watchOS) || os(visionOS)
        self.navigationBarTitleDisplayMode(.inline)
        #else
        self
        #endif
    }

    /// Applies insetGrouped list style on iOS and inset on macOS.
    @ViewBuilder
    public func adaptiveListStyle() -> some View {
        #if os(iOS)
        self.listStyle(.insetGrouped)
        #else
        self.listStyle(.inset)
        #endif
    }
}
