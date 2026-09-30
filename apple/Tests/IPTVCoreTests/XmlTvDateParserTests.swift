import XCTest
@testable import IPTVCore

final class XmlTvDateParserTests: XCTestCase {

    func testStandardUtcDateParsing() {
        // "20260930120000 +0000"
        let epoch = XmlTvDateParser.parseToEpochMillis("20260930120000 +0000")
        XCTAssertNotNil(epoch)

        // Verify date components in UTC
        let date = Date(timeIntervalSince1970: TimeInterval(epoch! / 1000))
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = TimeZone(secondsFromGMT: 0)!

        XCTAssertEqual(calendar.component(.year, from: date), 2026)
        XCTAssertEqual(calendar.component(.month, from: date), 9)
        XCTAssertEqual(calendar.component(.day, from: date), 30)
        XCTAssertEqual(calendar.component(.hour, from: date), 12)
        XCTAssertEqual(calendar.component(.minute, from: date), 0)
        XCTAssertEqual(calendar.component(.second, from: date), 0)
    }

    func testPositiveTimezoneOffset() {
        // "20260930140000 +0200" should equal "20260930120000 +0000"
        let epochUtc = XmlTvDateParser.parseToEpochMillis("20260930120000 +0000")
        let epochPlusTwo = XmlTvDateParser.parseToEpochMillis("20260930140000 +0200")
        XCTAssertEqual(epochUtc, epochPlusTwo)
    }

    func testNegativeTimezoneOffset() {
        // "20260930070000 -0500" should equal "20260930120000 +0000"
        let epochUtc = XmlTvDateParser.parseToEpochMillis("20260930120000 +0000")
        let epochMinusFive = XmlTvDateParser.parseToEpochMillis("20260930070000 -0500")
        XCTAssertEqual(epochUtc, epochMinusFive)
    }

    func testZSuffixTimezone() {
        let epochUtc = XmlTvDateParser.parseToEpochMillis("20260930120000 +0000")
        let epochZ = XmlTvDateParser.parseToEpochMillis("20260930120000 Z")
        XCTAssertEqual(epochUtc, epochZ)
    }

    func testInvalidDateStringsReturnNil() {
        XCTAssertNil(XmlTvDateParser.parseToEpochMillis(nil))
        XCTAssertNil(XmlTvDateParser.parseToEpochMillis(""))
        XCTAssertNil(XmlTvDateParser.parseToEpochMillis("invalid-date-format"))
    }
}
