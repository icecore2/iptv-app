import Foundation
import IPTVCore

/// Common protocol implemented by all external metadata providers.
public protocol MetadataProvider: Sendable {
    var source: MetadataSource { get }
    func search(query: String, year: Int?, language: String) async throws -> ProgrammeMetadata?
}

// MARK: - IMDb Provider

public final class ImdbMetadataProvider: MetadataProvider {
    public let source: MetadataSource = .imdb
    private let networkClient: NetworkClient

    public init(networkClient: NetworkClient = URLSessionNetworkClient()) {
        self.networkClient = networkClient
    }

    public func search(query: String, year: Int?, language: String) async throws -> ProgrammeMetadata? {
        let cleanQuery = query.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !cleanQuery.isEmpty else { return nil }

        let slug = cleanQuery.lowercased()
            .folding(options: .diacriticInsensitive, locale: .current)
            .filter { $0.isLetter || $0.isNumber || $0.isWhitespace }
            .replacingOccurrences(of: " ", with: "_")
        guard let firstChar = slug.first else { return nil }
        guard let suggestUrl = URL(string: "https://v3.sg.media-imdb.com/suggestion/x/\(firstChar)/\(slug).json") else {
            return nil
        }

        let suggestData: Data
        do {
            suggestData = try await networkClient.fetchData(from: suggestUrl, headers: [
                "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36"
            ])
        } catch {
            return nil
        }

        guard let json = try? JSONSerialization.jsonObject(with: suggestData) as? [String: Any],
              let items = json["d"] as? [[String: Any]] else {
            return nil
        }

        var bestItem: [String: Any]? = nil
        var bestId: String? = nil

        for item in items {
            guard let id = item["id"] as? String, id.hasPrefix("tt") else { continue }
            if let targetYear = year, let itemYear = item["y"] as? Int {
                if abs(itemYear - targetYear) <= 1 {
                    bestItem = item
                    bestId = id
                    break
                }
            } else if bestItem == nil {
                bestItem = item
                bestId = id
                if year == nil { break }
            }
        }

        guard let item = bestItem, let imdbId = bestId else { return nil }
        let title = (item["l"] as? String) ?? cleanQuery
        let itemYear = (item["y"] as? Int).map { String($0) } ?? year.map { String($0) }
        let imageObj = item["i"] as? [String: Any]
        let posterUrl = imageObj?["imageUrl"] as? String
        let actors = (item["s"] as? String)?.components(separatedBy: ", ").map { $0.trimmingCharacters(in: .whitespaces) } ?? []

        // Enrich via Cinemeta open metadata API
        var overview: String? = nil
        var rating: Float? = nil
        var trailerUrl: String? = nil
        var genres: [String] = []
        var directors: [String] = []

        let types = ["movie", "series"]
        for mediaType in types {
            guard let cinemetaUrl = URL(string: "https://v3-cinemeta.strem.io/meta/\(mediaType)/\(imdbId).json") else { continue }
            if let cinData = try? await networkClient.fetchData(from: cinemetaUrl, headers: [:]),
               let cinJson = try? JSONSerialization.jsonObject(with: cinData) as? [String: Any],
               let meta = cinJson["meta"] as? [String: Any] {
                overview = meta["description"] as? String
                if let rVal = meta["imdbRating"] as? Double {
                    rating = Float(rVal)
                } else if let rStr = meta["imdbRating"] as? String, let rVal = Double(rStr) {
                    rating = Float(rVal)
                }
                genres = (meta["genres"] as? [String]) ?? []
                directors = (meta["director"] as? [String]) ?? []

                // Trailer check
                if let trStr = meta["trailer"] as? String, !trStr.isEmpty {
                    trailerUrl = trStr
                } else if let trStreams = meta["trailerStreams"] as? [[String: Any]],
                          let first = trStreams.first,
                          let ytId = first["ytId"] as? String {
                    trailerUrl = "https://www.youtube.com/watch?v=\(ytId)"
                }
                break
            }
        }

        return ProgrammeMetadata(
            id: imdbId,
            source: .imdb,
            title: title,
            year: itemYear,
            overview: overview,
            rating: rating,
            posterUrl: posterUrl,
            trailerUrl: trailerUrl,
            genres: genres,
            director: directors,
            cast: actors,
            language: language
        )
    }
}

// MARK: - sratim.co.il Provider

public final class SratimMetadataProvider: MetadataProvider {
    public let source: MetadataSource = .sratim
    private let networkClient: NetworkClient
    private static let baseUrl = "https://www.sratim.co.il"

    public init(networkClient: NetworkClient = URLSessionNetworkClient()) {
        self.networkClient = networkClient
    }

    public func search(query: String, year: Int?, language: String) async throws -> ProgrammeMetadata? {
        let cleanQuery = query.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !cleanQuery.isEmpty else { return nil }

        guard let searchUrl = URL(string: "\(Self.baseUrl)/search.php") else { return nil }
        guard let encodedParam = cleanQuery.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed) else { return nil }
        let bodyData = "q=\(encodedParam)".data(using: .utf8)

        let headers: [String: String] = [
            "Content-Type": "application/x-www-form-urlencoded; charset=UTF-8",
            "Referer": "\(Self.baseUrl)/",
            "X-Requested-With": "XMLHttpRequest",
            "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36"
        ]

        let searchData: Data
        do {
            searchData = try await networkClient.postData(to: searchUrl, body: bodyData, headers: headers)
        } catch {
            return nil
        }

        guard let searchHtml = String(data: searchData, encoding: .utf8) else { return nil }

        // Find href="/tt\d+/" or simple link
        let regex = try? NSRegularExpression(pattern: "<a\\s+[^>]*href=\"(/tt\\d+[^\"\\s]*)\"", options: .caseInsensitive)
        let matches = regex?.matches(in: searchHtml, range: NSRange(searchHtml.startIndex..., in: searchHtml)) ?? []
        guard let firstMatch = matches.first,
              let range = Range(firstMatch.range(at: 1), in: searchHtml) else {
            return nil
        }
        let href = String(searchHtml[range])

        // Fetch detail page
        guard let detailUrl = URL(string: "\(Self.baseUrl)\(href)") else { return nil }
        guard let detailData = try? await networkClient.fetchData(from: detailUrl, headers: [
            "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36"
        ]),
        let detailHtml = String(data: detailData, encoding: .utf8) else {
            return nil
        }

        // Extract JSON-LD script
        let jsonLdRegex = try? NSRegularExpression(pattern: "<script[^>]*type=\"application/ld\\+json\"[^>]*>\\s*([\\{\\[].*?[\\}\\]])\\s*</script>", options: [.dotMatchesLineSeparators, .caseInsensitive])
        var title = cleanQuery
        var overview: String? = nil
        var posterUrl: String? = nil
        var releaseDate: String? = nil
        var rating: Float? = nil
        var genres: [String] = []
        var directors: [String] = []
        var cast: [String] = []

        if let ldMatch = jsonLdRegex?.firstMatch(in: detailHtml, range: NSRange(detailHtml.startIndex..., in: detailHtml)),
           let ldRange = Range(ldMatch.range(at: 1), in: detailHtml) {
            let jsonString = String(detailHtml[ldRange])
            if let data = jsonString.data(using: .utf8) {
                var jsonObjects: [[String: Any]] = []
                if let rootObj = try? JSONSerialization.jsonObject(with: data) as? [String: Any] {
                    if let graph = rootObj["@graph"] as? [[String: Any]] {
                        jsonObjects = graph
                    } else {
                        jsonObjects = [rootObj]
                    }
                } else if let rootArr = try? JSONSerialization.jsonObject(with: data) as? [[String: Any]] {
                    jsonObjects = rootArr
                }

                for obj in jsonObjects {
                    let type = (obj["@type"] as? String) ?? ""
                    if type.caseInsensitiveCompare("Movie") == .orderedSame || type.caseInsensitiveCompare("TVSeries") == .orderedSame {
                        if let name = obj["name"] as? String, !name.isEmpty { title = name }
                        overview = obj["description"] as? String
                        posterUrl = obj["image"] as? String
                        releaseDate = (obj["dateCreated"] as? String) ?? (obj["datePublished"] as? String)

                        if let aggRating = obj["aggregateRating"] as? [String: Any] {
                            if let rVal = aggRating["ratingValue"] as? Double {
                                rating = Float(rVal)
                            } else if let rStr = aggRating["ratingValue"] as? String, let rVal = Float(rStr) {
                                rating = rVal
                            }
                        }

                        if let genreStr = obj["genre"] as? String {
                            genres = genreStr.components(separatedBy: ",").map { $0.trimmingCharacters(in: .whitespaces) }
                        }

                        if let dirArr = obj["director"] as? [[String: Any]] {
                            directors = dirArr.compactMap { $0["name"] as? String }
                        }
                        if let actArr = obj["actor"] as? [[String: Any]] {
                            cast = actArr.compactMap { $0["name"] as? String }
                        }
                        break
                    }
                }
            }
        }

        // Check for YouTube trailer embed
        var trailerUrl: String? = nil
        let ytRegex = try? NSRegularExpression(pattern: "(?:youtube\\.com/(?:watch\\?v=|embed/)|youtu\\.be/)([a-zA-Z0-9_\\-]{11})", options: .caseInsensitive)
        if let ytMatch = ytRegex?.firstMatch(in: detailHtml, range: NSRange(detailHtml.startIndex..., in: detailHtml)),
           let ytRange = Range(ytMatch.range(at: 1), in: detailHtml) {
            let ytId = String(detailHtml[ytRange])
            trailerUrl = "https://www.youtube.com/watch?v=\(ytId)"
        }

        let parsedYear = releaseDate?.prefix(4).description ?? year.map { String($0) }
        let extId = href.replacingOccurrences(of: "/", with: "")

        return ProgrammeMetadata(
            id: extId.isEmpty ? "sratim_\(cleanQuery)" : extId,
            source: .sratim,
            title: title,
            year: parsedYear,
            overview: overview,
            rating: rating,
            posterUrl: posterUrl,
            trailerUrl: trailerUrl,
            genres: genres,
            director: directors,
            cast: cast,
            language: "he"
        )
    }
}

// MARK: - Trakt Provider

public final class TraktMetadataProvider: MetadataProvider {
    public let source: MetadataSource = .trakt
    private let networkClient: NetworkClient
    private let clientIdProvider: @Sendable () -> String?
    public static let defaultClientId = "c2264c767aa412e8cb13a84511ef5b8429ecdaff29505876378e88e89f81ebc5"

    public init(
        networkClient: NetworkClient = URLSessionNetworkClient(),
        clientIdProvider: @escaping @Sendable () -> String? = { nil }
    ) {
        self.networkClient = networkClient
        self.clientIdProvider = clientIdProvider
    }

    public func search(query: String, year: Int?, language: String) async throws -> ProgrammeMetadata? {
        let cleanQuery = query.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !cleanQuery.isEmpty else { return nil }

        guard let encoded = cleanQuery.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed),
              let url = URL(string: "https://api.trakt.tv/search/movie,show?query=\(encoded)&extended=full") else {
            return nil
        }

        let clientId = clientIdProvider()?.trimmingCharacters(in: .whitespacesAndNewlines)
        let resolvedKey = (clientId != nil && !clientId!.isEmpty) ? clientId! : Self.defaultClientId

        let headers: [String: String] = [
            "Content-Type": "application/json",
            "trakt-api-version": "2",
            "trakt-api-key": resolvedKey
        ]

        let data: Data
        do {
            data = try await networkClient.fetchData(from: url, headers: headers)
        } catch {
            return nil
        }

        guard let items = try? JSONSerialization.jsonObject(with: data) as? [[String: Any]], !items.isEmpty else {
            return nil
        }

        var bestMediaObj: [String: Any]? = nil

        for item in items {
            let type = (item["type"] as? String) ?? "movie"
            guard let media = item[type] as? [String: Any] else { continue }
            if let targetYear = year, let mYear = media["year"] as? Int {
                if abs(mYear - targetYear) <= 1 {
                    bestMediaObj = media
                    break
                }
            } else if bestMediaObj == nil {
                bestMediaObj = media
                if year == nil { break }
            }
        }

        guard let media = bestMediaObj ?? (items.first?["movie"] as? [String: Any]) ?? (items.first?["show"] as? [String: Any]) else {
            return nil
        }

        let title = (media["title"] as? String) ?? cleanQuery
        let itemYear = (media["year"] as? Int).map { String($0) } ?? year.map { String($0) }
        let overview = media["overview"] as? String
        let rating = (media["rating"] as? Double).map { Float($0) }
        let trailerUrl = media["trailer"] as? String
        let genres = (media["genres"] as? [String]) ?? []

        let ids = media["ids"] as? [String: Any]
        let slug = ids?["slug"] as? String
        let imdbId = ids?["imdb"] as? String
        let posterUrl = imdbId.map { "https://images.metahub.space/poster/medium/\($0)/img" }

        let externalId = slug ?? imdbId ?? "trakt_\(cleanQuery)"

        return ProgrammeMetadata(
            id: externalId,
            source: .trakt,
            title: title,
            year: itemYear,
            overview: overview,
            rating: rating,
            posterUrl: posterUrl,
            trailerUrl: trailerUrl,
            genres: genres,
            language: language
        )
    }
}

// MARK: - TheTVDB Provider

public final class TvdbMetadataProvider: MetadataProvider {
    public let source: MetadataSource = .tvdb
    private let networkClient: NetworkClient
    private let apiKeyProvider: @Sendable () -> String?
    public static let defaultApiKey = "f714249a-e8d9-487e-bb22-38b47e24699f"

    public init(
        networkClient: NetworkClient = URLSessionNetworkClient(),
        apiKeyProvider: @escaping @Sendable () -> String? = { nil }
    ) {
        self.networkClient = networkClient
        self.apiKeyProvider = apiKeyProvider
    }

    public func search(query: String, year: Int?, language: String) async throws -> ProgrammeMetadata? {
        let cleanQuery = query.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !cleanQuery.isEmpty else { return nil }

        let customKey = apiKeyProvider()?.trimmingCharacters(in: .whitespacesAndNewlines)
        let resolvedApiKey = (customKey != nil && !customKey!.isEmpty) ? customKey! : Self.defaultApiKey

        // Login to retrieve JWT
        guard let loginUrl = URL(string: "https://api4.thetvdb.com/v4/login") else { return nil }
        let loginPayload = ["apikey": resolvedApiKey]
        guard let body = try? JSONSerialization.data(withJSONObject: loginPayload) else { return nil }

        var token: String? = nil
        if let loginData = try? await networkClient.postData(to: loginUrl, body: body, headers: ["Content-Type": "application/json"]),
           let loginJson = try? JSONSerialization.jsonObject(with: loginData) as? [String: Any],
           let dataObj = loginJson["data"] as? [String: Any] {
            token = dataObj["token"] as? String
        }

        guard let authToken = token else { return nil }

        guard let encoded = cleanQuery.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed),
              let searchUrl = URL(string: "https://api4.thetvdb.com/v4/search?query=\(encoded)&type=movie,series") else {
            return nil
        }

        guard let searchData = try? await networkClient.fetchData(from: searchUrl, headers: [
            "Authorization": "Bearer \(authToken)",
            "Content-Type": "application/json"
        ]),
        let searchJson = try? JSONSerialization.jsonObject(with: searchData) as? [String: Any],
        let results = searchJson["data"] as? [[String: Any]], !results.isEmpty else {
            return nil
        }

        var bestItem = results.first!
        if let targetYear = year {
            for item in results {
                if let yStr = item["year"] as? String, let yInt = Int(yStr), abs(yInt - targetYear) <= 1 {
                    bestItem = item
                    break
                }
            }
        }

        let title = (bestItem["name"] as? String) ?? cleanQuery
        let itemYear = (bestItem["year"] as? String) ?? year.map { String($0) }
        let overview = bestItem["overview"] as? String
        let posterUrl = bestItem["image_url"] as? String
        let rating = (bestItem["score"] as? Double).map { Float($0) }
        let tvdbId = (bestItem["tvdb_id"] as? String) ?? (bestItem["id"] as? String) ?? "tvdb_\(cleanQuery)"

        return ProgrammeMetadata(
            id: tvdbId,
            source: .tvdb,
            title: title,
            year: itemYear,
            overview: overview,
            rating: rating,
            posterUrl: posterUrl,
            language: language
        )
    }
}

// MARK: - ProgrammeMetadataRepository

public final class ProgrammeMetadataRepository: Sendable {
    private let imdbProvider: ImdbMetadataProvider
    private let sratimProvider: SratimMetadataProvider
    private let traktProvider: TraktMetadataProvider
    private let tvdbProvider: TvdbMetadataProvider

    // Thread-safe caching
    private let cache = NSCache<NSString, CachedMetadataWrapper>()

    public init(
        networkClient: NetworkClient = URLSessionNetworkClient(),
        traktClientIdProvider: @escaping @Sendable () -> String? = { nil },
        tvdbApiKeyProvider: @escaping @Sendable () -> String? = { nil }
    ) {
        self.imdbProvider = ImdbMetadataProvider(networkClient: networkClient)
        self.sratimProvider = SratimMetadataProvider(networkClient: networkClient)
        self.traktProvider = TraktMetadataProvider(networkClient: networkClient, clientIdProvider: traktClientIdProvider)
        self.tvdbProvider = TvdbMetadataProvider(networkClient: networkClient, apiKeyProvider: tvdbApiKeyProvider)
        cache.countLimit = 500
    }

    private func cacheKey(source: MetadataSource, language: String, title: String) -> NSString {
        return "\(source.rawValue)_\(language.lowercased())_\(title.trimmingCharacters(in: .whitespacesAndNewlines).lowercased())" as NSString
    }

    public func getCached(rawTitle: String, source: MetadataSource = .auto, language: String = "en") -> ProgrammeMetadata? {
        let cleaned = TitleCleaner.clean(rawTitle)
        if source != .auto {
            return cache.object(forKey: cacheKey(source: source, language: language, title: cleaned.cleanTitle))?.metadata
        }
        for src in [MetadataSource.imdb, .sratim, .trakt, .tvdb] {
            if let found = cache.object(forKey: cacheKey(source: src, language: language, title: cleaned.cleanTitle))?.metadata {
                return found
            }
        }
        return nil
    }

    public func resolveMetadata(
        rawTitle: String,
        preferredSource: MetadataSource = .auto,
        language: String = "en"
    ) async -> ProgrammeMetadata? {
        let cleaned = TitleCleaner.clean(rawTitle)
        let cleanTitle = cleaned.cleanTitle
        guard !cleanTitle.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else { return nil }
        let yearInt = cleaned.yearHint.flatMap { Int($0) }

        // Check cache first
        if let cached = getCached(rawTitle: rawTitle, source: preferredSource, language: language) {
            return cached
        }

        let order = orderedSources(preferred: preferredSource, language: language)

        for src in order {
            if let result = try? await searchSource(src, query: cleanTitle, year: yearInt, language: language) {
                cache.setObject(CachedMetadataWrapper(metadata: result), forKey: cacheKey(source: src, language: language, title: cleanTitle))
                return result
            }
        }

        return nil
    }

    public func resolveAllSources(
        rawTitle: String,
        language: String = "en"
    ) async -> [MetadataSource: ProgrammeMetadata] {
        let cleaned = TitleCleaner.clean(rawTitle)
        let cleanTitle = cleaned.cleanTitle
        guard !cleanTitle.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else { return [:] }
        let yearInt = cleaned.yearHint.flatMap { Int($0) }

        var results: [MetadataSource: ProgrammeMetadata] = [:]

        await withTaskGroup(of: (MetadataSource, ProgrammeMetadata?).self) { group in
            for src in [MetadataSource.imdb, .sratim, .trakt, .tvdb] {
                group.addTask { [self] in
                    let meta = try? await self.searchSource(src, query: cleanTitle, year: yearInt, language: language)
                    return (src, meta)
                }
            }

            for await (src, meta) in group {
                if let m = meta {
                    results[src] = m
                    cache.setObject(CachedMetadataWrapper(metadata: m), forKey: cacheKey(source: src, language: language, title: cleanTitle))
                }
            }
        }

        return results
    }

    private func searchSource(_ source: MetadataSource, query: String, year: Int?, language: String) async throws -> ProgrammeMetadata? {
        switch source {
        case .imdb:
            return try await imdbProvider.search(query: query, year: year, language: language)
        case .sratim:
            return try await sratimProvider.search(query: query, year: year, language: language)
        case .trakt:
            return try await traktProvider.search(query: query, year: year, language: language)
        case .tvdb:
            return try await tvdbProvider.search(query: query, year: year, language: language)
        case .auto:
            return nil
        }
    }

    private func orderedSources(preferred: MetadataSource, language: String) -> [MetadataSource] {
        if preferred != .auto {
            var list = [preferred]
            for s in [MetadataSource.imdb, .trakt, .sratim, .tvdb] where s != preferred {
                list.append(s)
            }
            return list
        }

        let isHebrew = language.lowercased().hasPrefix("he") || language.lowercased().hasPrefix("iw")
        if isHebrew {
            return [.sratim, .imdb, .trakt, .tvdb]
        } else {
            return [.imdb, .trakt, .tvdb, .sratim]
        }
    }
}

private final class CachedMetadataWrapper: @unchecked Sendable {
    let metadata: ProgrammeMetadata
    init(metadata: ProgrammeMetadata) {
        self.metadata = metadata
    }
}
