import XCTest
@testable import IPTVCore

final class CatchupResolverTests: XCTestCase {

    private let sampleProgramme = EpgProgramme(
        channelId: "ch1",
        title: "Morning News",
        startEpochMillis: 1700000000000, // 1700000000 sec
        stopEpochMillis: 1700003600000,  // 1700003600 sec (1 hour duration)
        descriptionText: "Daily morning news report"
    )

    func testBuildVodUrlWhenChannelIsDirectVodReturnsOriginalStreamUrl() {
        let vodChannel = M3uItem(
            id: "movie1",
            name: "Test Movie",
            streamUrl: "https://cdn.example.com/movies/sample.mp4",
            group: "Movies"
        )

        let url = CatchupResolver.buildVodUrl(channel: vodChannel, programme: sampleProgramme)
        XCTAssertEqual(url, "https://cdn.example.com/movies/sample.mp4")
    }

    func testBuildVodUrlWhenCatchupSourceTemplateSpecifiedReplacesPlaceholders() {
        let channel = M3uItem(
            id: "ch1",
            name: "Channel 1",
            streamUrl: "http://iptv.server.com/live/ch1.m3u8",
            tvgId: "tvg_ch1",
            catchup: "append",
            catchupSource: "http://iptv.server.com/archive/${catchup-id}/${start}/${duration}.m3u8"
        )

        let url = CatchupResolver.buildVodUrl(channel: channel, programme: sampleProgramme)
        let expected = "http://iptv.server.com/archive/tvg_ch1/1700000000/3600.m3u8"
        XCTAssertEqual(url, expected)
    }

    func testBuildVodUrlWhenCatchupSourceIsQueryTemplateAppendsToStreamUrl() {
        let channel = M3uItem(
            id: "ch1",
            name: "Channel 1",
            streamUrl: "http://iptv.server.com/live/ch1.m3u8",
            catchup: "append",
            catchupSource: "?utc=${start}&lutc=${end}"
        )

        let url = CatchupResolver.buildVodUrl(channel: channel, programme: sampleProgramme)
        let expected = "http://iptv.server.com/live/ch1.m3u8?utc=1700000000&lutc=1700003600"
        XCTAssertEqual(url, expected)
    }

    func testBuildVodUrlWhenFlussonicCatchupFormatsTimeshiftAbsUrl() {
        let channel = M3uItem(
            id: "ch1",
            name: "Channel 1",
            streamUrl: "http://flussonic.server.com/live/stream.m3u8",
            catchup: "flussonic"
        )

        let url = CatchupResolver.buildVodUrl(channel: channel, programme: sampleProgramme)
        let expected = "http://flussonic.server.com/live/timeshift_abs-1700000000.m3u8"
        XCTAssertEqual(url, expected)
    }

    func testBuildVodUrlWhenXtreamCodesCatchupFormatsTimeshiftUrl() {
        let channel = M3uItem(
            id: "ch1",
            name: "Channel 1",
            streamUrl: "http://xc.server.com:8080/live/user1/pass1/12345.ts",
            catchup: "xc"
        )

        let url = CatchupResolver.buildVodUrl(channel: channel, programme: sampleProgramme)
        XCTAssertTrue(url.contains("/timeshift/user1/pass1/60/"))
        XCTAssertTrue(url.hasSuffix("/12345.ts"))
    }

    func testBuildVodUrlWhenDefaultAppendFallbackAppendsUtcParams() {
        let channel = M3uItem(
            id: "ch1",
            name: "Channel 1",
            streamUrl: "http://stream.server.com/live.m3u8"
        )

        let url = CatchupResolver.buildVodUrl(channel: channel, programme: sampleProgramme)
        XCTAssertEqual(url, "http://stream.server.com/live.m3u8?utc=1700000000&lutc=1700003600")
    }

    func testHasCatchupSupport() {
        let chWithoutCatchup = M3uItem(id: "1", name: "Ch1", streamUrl: "http://live.m3u8")
        let chWithCatchup = M3uItem(id: "2", name: "Ch2", streamUrl: "http://live.m3u8", catchup: "append")
        let chVod = M3uItem(id: "3", name: "Ch3", streamUrl: "http://movie.mp4")

        XCTAssertFalse(CatchupResolver.hasCatchupSupport(channel: chWithoutCatchup))
        XCTAssertTrue(CatchupResolver.hasCatchupSupport(channel: chWithCatchup))
        XCTAssertTrue(CatchupResolver.hasCatchupSupport(channel: chVod))
    }
}
