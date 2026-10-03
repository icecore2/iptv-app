import XCTest
@testable import IPTVCore

final class ProgrammeMetadataTests: XCTestCase {

    func testTitleCleanerStripsResolutionAndNoise() {
        let raw = "Inception (2010) [1080p] [HEVC] [Multi] [AAC]"
        let cleaned = TitleCleaner.clean(raw)
        XCTAssertEqual(cleaned.cleanTitle, "Inception")
        XCTAssertEqual(cleaned.yearHint, "2010")
    }

    func testTitleCleanerExtractsStandaloneYear() {
        let raw = "The Matrix 1999 4k Remux"
        let cleaned = TitleCleaner.clean(raw)
        XCTAssertEqual(cleaned.cleanTitle, "The Matrix")
        XCTAssertEqual(cleaned.yearHint, "1999")
    }

    func testTitleCleanerStripsSeasonAndEpisodeTags() {
        let raw = "Breaking Bad S01 E05 720p HDTV"
        let cleaned = TitleCleaner.clean(raw)
        XCTAssertEqual(cleaned.cleanTitle, "Breaking Bad")
    }

    func testTitleCleanerHandlesHebrewNames() {
        let raw = "פאודה (2015) [עונה 1] [FHD]"
        let cleaned = TitleCleaner.clean(raw)
        XCTAssertTrue(cleaned.cleanTitle.contains("פאודה"))
        XCTAssertEqual(cleaned.yearHint, "2015")
    }

    func testProgrammeMetadataRatingFormatting() {
        let metaWithRating = ProgrammeMetadata(
            id: "tt1375666",
            source: .imdb,
            title: "Inception",
            rating: 8.8
        )
        XCTAssertEqual(metaWithRating.formattedRating, "★ 8.8")
        XCTAssertFalse(metaWithRating.hasTrailer)

        let metaWithTrailer = ProgrammeMetadata(
            id: "tt1375666",
            source: .imdb,
            title: "Inception",
            trailerUrl: "https://www.youtube.com/watch?v=YoHD9XEInc0"
        )
        XCTAssertTrue(metaWithTrailer.hasTrailer)
    }

    func testMetadataSourceDisplayNamesAndBadges() {
        XCTAssertEqual(MetadataSource.imdb.badgeLabel, "IMDb")
        XCTAssertEqual(MetadataSource.trakt.badgeLabel, "TRAKT")
        XCTAssertEqual(MetadataSource.sratim.badgeLabel, "סרטים")
        XCTAssertEqual(MetadataSource.tvdb.badgeLabel, "TVDB")
        XCTAssertEqual(MetadataSource.auto.badgeLabel, "EPG+")
    }

    func testProgrammeMetadataCodableRoundtrip() throws {
        let original = ProgrammeMetadata(
            id: "tt1375666",
            source: .imdb,
            title: "Inception",
            year: "2010",
            overview: "A thief who steals corporate secrets.",
            rating: 8.8,
            posterUrl: "https://example.com/poster.jpg",
            trailerUrl: "https://youtube.com/watch?v=123",
            genres: ["Action", "Sci-Fi"],
            director: ["Christopher Nolan"],
            cast: ["Leonardo DiCaprio"],
            language: "en"
        )

        let data = try JSONEncoder().encode(original)
        let decoded = try JSONDecoder().decode(ProgrammeMetadata.self, data: data)

        XCTAssertEqual(decoded.id, original.id)
        XCTAssertEqual(decoded.title, original.title)
        XCTAssertEqual(decoded.source, original.source)
        XCTAssertEqual(decoded.year, original.year)
        XCTAssertEqual(decoded.rating, original.rating)
        XCTAssertEqual(decoded.genres, original.genres)
    }
}
