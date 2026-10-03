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
}
