// swift-tools-version: 5.9
// The swift-tools-version declares the minimum version of Swift required to build this package.

import PackageDescription

let package = Package(
    name: "IPTVApple",
    defaultLocalization: "en",
    platforms: [
        .iOS(.v17),
        .macOS(.v14),
        .tvOS(.v17)
    ],
    products: [
        .library(
            name: "IPTVCore",
            targets: ["IPTVCore"]
        ),
        .library(
            name: "IPTVData",
            targets: ["IPTVData"]
        ),
        .library(
            name: "IPTVPlayer",
            targets: ["IPTVPlayer"]
        ),
        .library(
            name: "IPTVUI",
            targets: ["IPTVUI"]
        )
    ],
    dependencies: [],
    targets: [
        // Core pure Swift logic: models, parsers, fuzzy matchers, catchup resolver
        .target(
            name: "IPTVCore",
            dependencies: [],
            path: "Sources/IPTVCore"
        ),
        // Networking, caching, persistence, repositories
        .target(
            name: "IPTVData",
            dependencies: ["IPTVCore"],
            path: "Sources/IPTVData"
        ),
        // AVPlayer engine, PiP controller, time-shift live buffer, NowPlaying
        .target(
            name: "IPTVPlayer",
            dependencies: ["IPTVCore", "IPTVData"],
            path: "Sources/IPTVPlayer"
        ),
        // Multiplatform SwiftUI views, view models, navigation for iOS and macOS
        .target(
            name: "IPTVUI",
            dependencies: ["IPTVCore", "IPTVData", "IPTVPlayer"],
            path: "Sources/IPTVUI"
        ),
        // Test targets
        .testTarget(
            name: "IPTVCoreTests",
            dependencies: ["IPTVCore"],
            path: "Tests/IPTVCoreTests"
        ),
        .testTarget(
            name: "IPTVDataTests",
            dependencies: ["IPTVCore", "IPTVData"],
            path: "Tests/IPTVDataTests"
        ),
        .testTarget(
            name: "IPTVPlayerTests",
            dependencies: ["IPTVCore", "IPTVPlayer"],
            path: "Tests/IPTVPlayerTests"
        )
    ]
)
