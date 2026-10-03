import Foundation

/// Supported external metadata providers for enriched programme and movie details.
public enum MetadataSource: String, Codable, CaseIterable, Identifiable, Sendable {
    case imdb = "IMDb"
    case trakt = "Trakt"
    case sratim = "sratim.co.il"
    case tvdb = "TheTVDB"
    case auto = "Auto"

    public var id: String { rawValue }

    public var displayName: String {
        switch self {
        case .imdb: return "IMDb"
        case .trakt: return "Trakt.tv"
        case .sratim: return "סרטים (sratim.co.il)"
        case .tvdb: return "TheTVDB"
        case .auto: return "Auto (Smart Priority)"
        }
    }

    public var badgeLabel: String {
        switch self {
        case .imdb: return "IMDb"
        case .trakt: return "TRAKT"
        case .sratim: return "סרטים"
        case .tvdb: return "TVDB"
        case .auto: return "EPG+"
        }
    }
}

/// Rich metadata for an EPG programme or VOD stream item.
public struct ProgrammeMetadata: Identifiable, Hashable, Codable, Sendable {
    public let id: String
    public let source: MetadataSource
    public let title: String
    public let originalTitle: String?
    public let year: String?
    public let overview: String?
    public let rating: Float?
    public let posterUrl: String?
    public let trailerUrl: String?
    public let genres: [String]
    public let director: [String]
    public let cast: [String]
    public let language: String

    public init(
        id: String,
        source: MetadataSource,
        title: String,
        originalTitle: String? = nil,
        year: String? = nil,
        overview: String? = nil,
        rating: Float? = nil,
        posterUrl: String? = nil,
        trailerUrl: String? = nil,
        genres: [String] = [],
        director: [String] = [],
        cast: [String] = [],
        language: String = "en"
    ) {
        self.id = id
        self.source = source
        self.title = title
        self.originalTitle = originalTitle
        self.year = year
        self.overview = overview
        self.rating = rating
        self.posterUrl = posterUrl
        self.trailerUrl = trailerUrl
        self.genres = genres
        self.director = director
        self.cast = cast
        self.language = language
    }

    public var formattedRating: String? {
        guard let r = rating else { return nil }
        return String(format: "★ %.1f", r)
    }

    public var hasTrailer: Bool {
        guard let url = trailerUrl else { return false }
        return !url.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
    }
}

/// Cleaned title and extracted year hint result.
public struct TitleCleanResult: Equatable, Sendable {
    public let cleanTitle: String
    public let yearHint: String?

    public init(cleanTitle: String, yearHint: String?) {
        self.cleanTitle = cleanTitle
        self.yearHint = yearHint
    }
}

/// Cleans IPTV stream/programme titles by stripping broadcast tags, brackets, resolutions, and season tags.
public enum TitleCleaner {
    private static let noisePatterns: [String] = [
        // Resolutions and formats
        "(?i)\\b(1080p|720p|480p|2160p|4k|uhd|fhd|hd|sd)\\b",
        "(?i)\\b(hevc|h264|h265|x264|x265|avc|10bit)\\b",
        "(?i)\\b(bluray|web-dl|webrip|hdtv|dvdrip|remux)\\b",
        "(?i)\\b(aac|ac3|dts|dd5\\.1|5\\.1|stereo)\\b",
        // Language tags
        "(?i)\\b(heb|eng|rus|fra|ger|ita|spa|sub|dub|multi)\\b",
        // Season / episode tags
        "(?i)\\bS\\d{1,2}\\s*E\\d{1,2}\\b",
        "(?i)\\bS\\d{1,2}\\b",
        "(?i)\\bE\\d{1,2}\\b",
        "(?i)\\bSeason\\s*\\d+\\b",
        "(?i)\\bEpisode\\s*\\d+\\b"
    ]

    public static func clean(_ rawTitle: String) -> TitleCleanResult {
        var text = rawTitle.trimmingCharacters(in: .whitespacesAndNewlines)
        var extractedYear: String? = nil

        // 1. Extract 4-digit year enclosed in parentheses or brackets: (2023) or [2023]
        if let yearMatch = text.range(of: "[\\(\\[](\\d{4})[\\)\\]]", options: .regularExpression) {
            let matchedStr = String(text[yearMatch])
            let digits = matchedStr.filter { $0.isNumber }
            if digits.count == 4 {
                extractedYear = digits
                text.removeSubrange(yearMatch)
            }
        }

        // 2. Strip noise patterns
        for pattern in noisePatterns {
            text = text.replacingOccurrences(of: pattern, with: " ", options: .regularExpression)
        }

        // 3. Remove lingering square and curly bracket content
        text = text.replacingOccurrences(of: "\\[[^\\]]*\\]", with: " ", options: .regularExpression)
        text = text.replacingOccurrences(of: "\\{[^\\}]*\\}", with: " ", options: .regularExpression)

        // 4. If year was not found in brackets, find standalone 4-digit year between 1920 and 2099
        if extractedYear == nil {
            if let standaloneYearRange = text.range(of: "\\b(19[2-9]\\d|20[0-9]\\d)\\b", options: .regularExpression) {
                extractedYear = String(text[standaloneYearRange])
                text.removeSubrange(standaloneYearRange)
            }
        }

        // 5. Clean up stray punctuation, hyphens, and whitespace
        text = text.replacingOccurrences(of: "[-_.:|]+", with: " ", options: .regularExpression)
        text = text.replacingOccurrences(of: "\\s+", with: " ", options: .regularExpression)
        let cleaned = text.trimmingCharacters(in: .whitespacesAndNewlines)

        return TitleCleanResult(
            cleanTitle: cleaned.isEmpty ? rawTitle : cleaned,
            yearHint: extractedYear
        )
    }
}
