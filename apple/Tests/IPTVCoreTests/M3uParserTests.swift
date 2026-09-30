import XCTest
@testable import IPTVCore

final class M3uParserTests: XCTestCase {

    private let parser = M3uParser()

    func testEmptyInputReturnsEmptyPlaylist() {
        let playlist = parser.parse(content: "")
        XCTAssertTrue(playlist.items.isEmpty)
        XCTAssertTrue(playlist.groups.isEmpty)
    }

    func testHeaderDirectivesExtractsUrlTvgAndAttributes() {
        let content = """
        #EXTM3U url-tvg="http://example.com/epg.xml.gz" tvg-shift="1"
        #EXTINF:-1 tvg-id="cnn" tvg-name="CNN HD" group-title="News",CNN News
        https://example.com/stream/cnn.m3u8
        """

        let playlist = parser.parse(content: content)
        XCTAssertEqual(playlist.epgUrl, "http://example.com/epg.xml.gz")
        XCTAssertEqual(playlist.items.count, 1)
        XCTAssertEqual(playlist.headerAttributes["tvg-shift"], "1")
    }

    func testAlternativeHeaderDirectiveExtractsXTvgUrl() {
        let content = """
        #EXTM3U x-tvg-url="https://epg.test/guide.xml"
        #EXTINF:-1,Sample TV
        https://example.com/stream/sample.m3u8
        """

        let playlist = parser.parse(content: content)
        XCTAssertEqual(playlist.epgUrl, "https://epg.test/guide.xml")
        XCTAssertEqual(playlist.items.count, 1)
        XCTAssertEqual(playlist.items[0].name, "Sample TV")
    }

    func testBasicChannelEntryParsesTitleAndStreamUrl() {
        let content = """
        #EXTM3U
        #EXTINF:-1,BBC News 24
        http://stream.bbc.co.uk/live.m3u8
        """

        let playlist = parser.parse(content: content)
        XCTAssertEqual(playlist.items.count, 1)
        let item = playlist.items[0]
        XCTAssertEqual(item.name, "BBC News 24")
        XCTAssertEqual(item.streamUrl, "http://stream.bbc.co.uk/live.m3u8")
        XCTAssertEqual(item.group, "General")
    }

    func testTvgAttributesExtractsIdNameLogoAndGroup() {
        let content = """
        #EXTM3U
        #EXTINF:-1 tvg-id="discovery.us" tvg-name="Discovery HD" tvg-logo="https://icons.com/disc.png" group-title="Documentaries" radio="false",Discovery Channel
        https://stream.discovery.com/hls/live.m3u8
        """

        let playlist = parser.parse(content: content)
        XCTAssertEqual(playlist.items.count, 1)
        let item = playlist.items[0]
        XCTAssertEqual(item.tvgId, "discovery.us")
        XCTAssertEqual(item.tvgName, "Discovery HD")
        XCTAssertEqual(item.logoUrl, "https://icons.com/disc.png")
        XCTAssertEqual(item.group, "Documentaries")
        XCTAssertEqual(item.name, "Discovery Channel")
        XCTAssertFalse(item.isRadio)
        XCTAssertEqual(playlist.groups, ["Documentaries"])
    }

    func testCommasAndQuotesInAttributes() {
        let content = """
        #EXTM3U
        #EXTINF:-1 tvg-name="News, 24/7" group-title="News & Weather",News, 24/7 (Live, HD)
        https://stream.news.com/live.m3u8
        """

        let playlist = parser.parse(content: content)
        XCTAssertEqual(playlist.items.count, 1)
        let item = playlist.items[0]
        XCTAssertEqual(item.tvgName, "News, 24/7")
        XCTAssertEqual(item.group, "News & Weather")
        XCTAssertEqual(item.name, "News, 24/7 (Live, HD)")
    }

    func testVlcOptionsExtractsHeaders() {
        let content = """
        #EXTM3U
        #EXTINF:-1 tvg-id="sports1",Sky Sports
        #EXTVLCOPT:http-user-agent=CustomUserAgent/1.0
        #EXTVLCOPT:http-referrer=https://sports.com
        https://stream.sports.com/sky.m3u8
        """

        let playlist = parser.parse(content: content)
        XCTAssertEqual(playlist.items.count, 1)
        let item = playlist.items[0]
        XCTAssertEqual(item.headers["User-Agent"], "CustomUserAgent/1.0")
        XCTAssertEqual(item.headers["Referer"], "https://sports.com")
    }

    func testLargePlaylistParsing() {
        var lines = ["#EXTM3U"]
        let channelCount = 200
        for i in 1...channelCount {
            let group = (i % 2 == 0) ? "Sports" : "Cinema"
            lines.append("#EXTINF:-1 tvg-id=\"ch\(i)\" group-title=\"\(group)\",Channel \(i)")
            lines.append("https://stream.example.com/channel\(i).m3u8")
        }

        let content = lines.joined(separator: "\n")
        let playlist = parser.parse(content: content)
        XCTAssertEqual(playlist.items.count, channelCount)
        XCTAssertEqual(playlist.groups.sorted(), ["Cinema", "Sports"])
    }

    func testCatchupAttributesExtractsCatchupSourceAndDays() {
        let content = """
        #EXTM3U
        #EXTINF:-1 tvg-id="cnn" catchup="append" catchup-source="?utc=${start}&lutc=${timestamp}" catchup-days="7",CNN
        https://stream.cnn.com/live.m3u8
        """

        let playlist = parser.parse(content: content)
        XCTAssertEqual(playlist.items.count, 1)
        let item = playlist.items[0]
        XCTAssertEqual(item.catchup, "append")
        XCTAssertEqual(item.catchupSource, "?utc=${start}&lutc=${timestamp}")
        XCTAssertEqual(item.catchupDays, 7)
    }

    func testHeaderCatchupAttributesInheritedByItems() {
        let content = """
        #EXTM3U catchup="default" catchup-days="3" catchup-source="http://server.com/archive/${start}.m3u8"
        #EXTINF:-1 tvg-id="bbc",BBC One
        https://stream.bbc.com/live.m3u8
        #EXTINF:-1 tvg-id="itv" catchup="shift" catchup-days="5",ITV
        https://stream.itv.com/live.m3u8
        """

        let playlist = parser.parse(content: content)
        XCTAssertEqual(playlist.items.count, 2)

        // Item 1 inherits from header
        let bbc = playlist.items[0]
        XCTAssertEqual(bbc.catchup, "default")
        XCTAssertEqual(bbc.catchupDays, 3)
        XCTAssertEqual(bbc.catchupSource, "http://server.com/archive/${start}.m3u8")

        // Item 2 overrides header
        let itv = playlist.items[1]
        XCTAssertEqual(itv.catchup, "shift")
        XCTAssertEqual(itv.catchupDays, 5)
        XCTAssertEqual(itv.catchupSource, "http://server.com/archive/${start}.m3u8")
    }
}
