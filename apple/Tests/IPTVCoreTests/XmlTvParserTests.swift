import XCTest
@testable import IPTVCore

final class XmlTvParserTests: XCTestCase {

    private let parser = XmlTvParser()

    func testParseXmlTvChannelsAndProgrammes() {
        let xml = """
        <?xml version="1.0" encoding="UTF-8"?>
        <tv generator-info-name="TestGenerator">
            <channel id="cnn">
                <display-name>CNN International</display-name>
                <icon src="https://images.com/cnn.png"/>
            </channel>
            <channel id="bbc1">
                <display-name>BBC One</display-name>
            </channel>
            
            <programme start="20260922100000 +0000" stop="20260922110000 +0000" channel="cnn">
                <title lang="en">Morning News</title>
                <desc lang="en">Comprehensive world news updates.</desc>
                <category>News</category>
                <icon src="https://images.com/show1.png"/>
            </programme>
            <programme start="20260922110000 +0000" stop="20260922120000 +0000" channel="cnn">
                <title lang="en">Connect the World</title>
                <desc lang="en">Interviews with world leaders.</desc>
                <category>News</category>
            </programme>
            <programme start="20260922100000 +0000" stop="20260922103000 +0000" channel="bbc1">
                <title>Breakfast</title>
            </programme>
        </tv>
        """

        let epgData = parser.parse(xmlString: xml)
        XCTAssertEqual(epgData.channels.count, 2)

        let cnn = epgData.channels["cnn"]
        XCTAssertNotNil(cnn)
        XCTAssertEqual(cnn?.displayName, "CNN International")
        XCTAssertEqual(cnn?.iconUrl, "https://images.com/cnn.png")

        let bbc = epgData.channels["bbc1"]
        XCTAssertNotNil(bbc)
        XCTAssertEqual(bbc?.displayName, "BBC One")

        XCTAssertEqual(epgData.programmes.count, 3)
        let p1 = epgData.programmes[0]
        XCTAssertEqual(p1.channelId, "cnn")
        XCTAssertEqual(p1.title, "Morning News")
        XCTAssertEqual(p1.descriptionText, "Comprehensive world news updates.")
        XCTAssertEqual(p1.category, "News")
        XCTAssertEqual(p1.iconUrl, "https://images.com/show1.png")

        let p3 = epgData.programmes[2]
        XCTAssertEqual(p3.channelId, "bbc1")
        XCTAssertEqual(p3.title, "Breakfast")
    }

    func testParseXmlTvEmptyOrInvalidInputReturnsEmptyData() {
        let emptyData = parser.parse(data: Data())
        XCTAssertTrue(emptyData.channels.isEmpty)
        XCTAssertTrue(emptyData.programmes.isEmpty)
    }
}
