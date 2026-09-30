import XCTest
@testable import IPTVCore

final class EpgMatcherTests: XCTestCase {

    private let epgData = EpgData(
        channels: [
            "cnn.us": EpgChannel(id: "cnn.us", displayName: "CNN International"),
            "bbc.one": EpgChannel(id: "bbc.one", displayName: "BBC One HD")
        ],
        programmes: [
            EpgProgramme(channelId: "cnn.us", title: "Morning News", startEpochMillis: 1000, stopEpochMillis: 2000, descriptionText: "Early updates"),
            EpgProgramme(channelId: "cnn.us", title: "Afternoon Live", startEpochMillis: 2000, stopEpochMillis: 3000, descriptionText: "Midday news"),
            EpgProgramme(channelId: "cnn.us", title: "Prime Time", startEpochMillis: 3000, stopEpochMillis: 4000),
            EpgProgramme(channelId: "bbc.one", title: "Breakfast Show", startEpochMillis: 1000, stopEpochMillis: 2500)
        ]
    )

    private var matcher: EpgMatcher!

    override func setUp() {
        super.setUp()
        matcher = EpgMatcher(epgData: epgData)
    }

    func testMatchExactTvgId() {
        let channel = M3uItem(id: "1", name: "Any Name", streamUrl: "http://url", tvgId: "cnn.us")
        let matchedEpgChannelId = matcher.findEpgChannelId(channel: channel)
        XCTAssertEqual(matchedEpgChannelId, "cnn.us")
    }

    func testMatchNormalizedNameFallback() {
        let channel = M3uItem(
            id: "2",
            name: "BBC One FHD [UK]",
            streamUrl: "http://url",
            tvgId: nil
        )
        let matchedEpgChannelId = matcher.findEpgChannelId(channel: channel)
        XCTAssertEqual(matchedEpgChannelId, "bbc.one")
    }

    func testGetCurrentProgrammeAtTimestamp() {
        let channel = M3uItem(id: "1", name: "CNN", streamUrl: "http://url", tvgId: "cnn.us")
        let current = matcher.getCurrentProgramme(channel: channel, timestamp: 1500)
        XCTAssertNotNil(current)
        XCTAssertEqual(current?.title, "Morning News")

        let afternoon = matcher.getCurrentProgramme(channel: channel, timestamp: 2500)
        XCTAssertNotNil(afternoon)
        XCTAssertEqual(afternoon?.title, "Afternoon Live")
    }

    func testGetNextProgrammeAtTimestamp() {
        let channel = M3uItem(id: "1", name: "CNN", streamUrl: "http://url", tvgId: "cnn.us")
        let next = matcher.getNextProgramme(channel: channel, timestamp: 1500)
        XCTAssertNotNil(next)
        XCTAssertEqual(next?.title, "Afternoon Live")
    }

    func testCalculateProgress() {
        let programme = EpgProgramme(channelId: "cnn.us", title: "Morning News", startEpochMillis: 1000, stopEpochMillis: 2000)
        XCTAssertEqual(programme.progress(timestampMillis: 1000), 0.0, accuracy: 0.001)
        XCTAssertEqual(programme.progress(timestampMillis: 1500), 0.5, accuracy: 0.001)
        XCTAssertEqual(programme.progress(timestampMillis: 2000), 1.0, accuracy: 0.001)
        XCTAssertEqual(programme.progress(timestampMillis: 2500), 1.0, accuracy: 0.001)
        XCTAssertEqual(programme.progress(timestampMillis: 500), 0.0, accuracy: 0.001)
    }

    func testEnrichChannelWithEpg() {
        let channel = M3uItem(id: "1", name: "CNN", streamUrl: "http://url", tvgId: "cnn.us")
        let enriched = matcher.enrichChannel(channel: channel, timestamp: 1500)

        XCTAssertEqual(enriched.channel.name, "CNN")
        XCTAssertEqual(enriched.currentProgramme?.title, "Morning News")
        XCTAssertEqual(enriched.nextProgramme?.title, "Afternoon Live")
        XCTAssertEqual(enriched.progress, 0.5, accuracy: 0.001)
    }

    func testGetScheduleForChannel() {
        let channel = M3uItem(id: "1", name: "CNN", streamUrl: "http://url", tvgId: "cnn.us")
        let schedule = matcher.getSchedule(channel: channel)
        XCTAssertEqual(schedule.count, 3)
        XCTAssertEqual(schedule[0].title, "Morning News")
        XCTAssertEqual(schedule[2].title, "Prime Time")
    }
}
