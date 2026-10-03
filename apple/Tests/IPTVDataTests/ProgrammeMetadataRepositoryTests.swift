import XCTest
@testable import IPTVCore
@testable import IPTVData

private final class MockNetworkClient: NetworkClient, @unchecked Sendable {
    var responseHandler: @Sendable (URL, String, Data?) -> Data

    init(responseHandler: @escaping @Sendable (URL, String, Data?) -> Data) {
        self.responseHandler = responseHandler
    }

    func fetchData(from url: URL, headers: [String: String]) async throws -> Data {
        return responseHandler(url, "GET", nil)
    }

    func postData(to url: URL, body: Data?, headers: [String: String]) async throws -> Data {
        return responseHandler(url, "POST", body)
    }
}

final class ProgrammeMetadataRepositoryTests: XCTestCase {

    func testImdbProviderResolvesMovie() async throws {
        let mock = MockNetworkClient { url, method, body in
            let str = url.absoluteString
            if str.contains("v3.sg.media-imdb.com/suggestion") {
                return """
                {
                    "d": [
                        {
                            "id": "tt1375666",
                            "l": "Inception",
                            "y": 2010,
                            "i": { "imageUrl": "https://example.com/inception.jpg" },
                            "s": "Leonardo DiCaprio, Joseph Gordon-Levitt"
                        }
                    ]
                }
                """.data(using: .utf8)!
            } else if str.contains("v3-cinemeta.strem.io") {
                return """
                {
                    "meta": {
                        "name": "Inception",
                        "description": "A thief who steals corporate secrets.",
                        "imdbRating": "8.8",
                        "genres": ["Action", "Sci-Fi"],
                        "trailer": "https://www.youtube.com/watch?v=YoHD9XEInc0"
                    }
                }
                """.data(using: .utf8)!
            }
            return Data()
        }

        let provider = ImdbMetadataProvider(networkClient: mock)
        let meta = try await provider.search(query: "Inception", year: 2010, language: "en")

        XCTAssertNotNil(meta)
        XCTAssertEqual(meta?.id, "tt1375666")
        XCTAssertEqual(meta?.title, "Inception")
        XCTAssertEqual(meta?.year, "2010")
        XCTAssertEqual(meta?.source, .imdb)
        XCTAssertEqual(meta?.rating, 8.8)
        XCTAssertTrue(meta?.hasTrailer ?? false)
    }

    func testTraktProviderResolvesMovie() async throws {
        let mock = MockNetworkClient { url, method, body in
            return """
            [
                {
                    "type": "movie",
                    "movie": {
                        "title": "Interstellar",
                        "year": 2014,
                        "ids": {
                            "trakt": 12,
                            "slug": "interstellar-2014",
                            "imdb": "tt0816692"
                        },
                        "overview": "A team of explorers travel through a wormhole.",
                        "rating": 8.6,
                        "trailer": "https://youtube.com/watch?v=zSWdZVtXT7E",
                        "genres": ["adventure", "drama", "sci-fi"]
                    }
                }
            ]
            """.data(using: .utf8)!
        }

        let provider = TraktMetadataProvider(networkClient: mock)
        let meta = try await provider.search(query: "Interstellar", year: 2014, language: "en")

        XCTAssertNotNil(meta)
        XCTAssertEqual(meta?.id, "interstellar-2014")
        XCTAssertEqual(meta?.title, "Interstellar")
        XCTAssertEqual(meta?.year, "2014")
        XCTAssertEqual(meta?.source, .trakt)
    }

    func testRepositoryCachesResult() async throws {
        let mock = MockNetworkClient { url, method, body in
            let str = url.absoluteString
            if str.contains("v3.sg.media-imdb.com/suggestion") {
                return """
                {
                    "d": [
                        {
                            "id": "tt0133093",
                            "l": "The Matrix",
                            "y": 1999
                        }
                    ]
                }
                """.data(using: .utf8)!
            }
            return Data()
        }

        let repo = ProgrammeMetadataRepository(networkClient: mock)
        let first = await repo.resolveMetadata(rawTitle: "The Matrix 1999", preferredSource: .imdb)
        XCTAssertNotNil(first)

        let cached = repo.getCached(rawTitle: "The Matrix 1999", source: .imdb)
        XCTAssertNotNil(cached)
        XCTAssertEqual(cached?.id, "tt0133093")
    }
}
