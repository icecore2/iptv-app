import Foundation

/// Aspect ratio display scaling mode for video rendering.
public enum AspectRatioMode: String, Codable, CaseIterable, Sendable {
    case fit = "FIT"     // Letterbox / Pillarbox
    case zoom = "ZOOM"   // Crop to fill screen
    case fill = "FILL"   // Stretch to fill screen

    public var displayName: String {
        switch self {
        case .fit: return "Fit (16:9 Letterbox)"
        case .zoom: return "Zoom (Crop to Fill)"
        case .fill: return "Fill (Stretch Screen)"
        }
    }
}

/// Global application preferences and streaming configuration.
public struct AppSettings: Hashable, Codable, Sendable {
    // General & Channel List
    public var showChannelLogos: Bool
    public var enablePagination: Bool
    public var pageSize: Int
    public var showEpgInList: Bool
    public var autoLoadLastPlaylist: Bool

    // Player & Streaming
    public var bufferDurationSeconds: Int
    public var bufferStorageLimitMb: Int
    public var keepScreenOn: Bool
    public var fastChannelSwitching: Bool
    public var hardwareAcceleration: Bool
    public var defaultAspectRatio: AspectRatioMode
    public var showStreamInfoOverlay: Bool

    // Metadata Integration (IMDb, Trakt, sratim.co.il, TVDB)
    public var preferredMetadataSource: MetadataSource
    public var metadataLanguage: String
    public var traktClientId: String
    public var tvdbApiKey: String
    public var showInlineMetadataBadge: Bool

    public init(
        showChannelLogos: Bool = true,
        enablePagination: Bool = true,
        pageSize: Int = 50,
        showEpgInList: Bool = true,
        autoLoadLastPlaylist: Bool = false,
        bufferDurationSeconds: Int = 15,
        bufferStorageLimitMb: Int = 1024,
        keepScreenOn: Bool = true,
        fastChannelSwitching: Bool = true,
        hardwareAcceleration: Bool = true,
        defaultAspectRatio: AspectRatioMode = .fit,
        showStreamInfoOverlay: Bool = false,
        preferredMetadataSource: MetadataSource = .auto,
        metadataLanguage: String = "auto",
        traktClientId: String = "",
        tvdbApiKey: String = "",
        showInlineMetadataBadge: Bool = true
    ) {
        self.showChannelLogos = showChannelLogos
        self.enablePagination = enablePagination
        self.pageSize = pageSize
        self.showEpgInList = showEpgInList
        self.autoLoadLastPlaylist = autoLoadLastPlaylist
        self.bufferDurationSeconds = bufferDurationSeconds
        self.bufferStorageLimitMb = bufferStorageLimitMb
        self.keepScreenOn = keepScreenOn
        self.fastChannelSwitching = fastChannelSwitching
        self.hardwareAcceleration = hardwareAcceleration
        self.defaultAspectRatio = defaultAspectRatio
        self.showStreamInfoOverlay = showStreamInfoOverlay
        self.preferredMetadataSource = preferredMetadataSource
        self.metadataLanguage = metadataLanguage
        self.traktClientId = traktClientId
        self.tvdbApiKey = tvdbApiKey
        self.showInlineMetadataBadge = showInlineMetadataBadge
    }

    public static let bufferPresets: [(Int, String)] = [
        (5, "5s (Low Latency / Live Sports)"),
        (15, "15s (Balanced - Default)"),
        (30, "30s (High Stability)"),
        (60, "60s (Maximum Buffer / Weak WiFi)")
    ]

    public static let bufferStoragePresets: [(Int, String)] = [
        (256, "256 MB"),
        (512, "512 MB"),
        (1024, "1 GB (Default)"),
        (2048, "2 GB"),
        (4096, "4 GB")
    ]

    public static let pageSizeOptions: [Int] = [25, 50, 100, 200]

    public static let metadataLanguageOptions: [(String, String)] = [
        ("auto", "Auto (Detect / Default)"),
        ("en", "English"),
        ("he", "עברית (Hebrew)"),
        ("es", "Español (Spanish)"),
        ("fr", "Français (French)"),
        ("de", "Deutsch (German)"),
        ("it", "Italiano (Italian)"),
        ("ru", "Русский (Russian)"),
        ("ar", "العربية (Arabic)"),
        ("pt", "Português (Portuguese)")
    ]
}
