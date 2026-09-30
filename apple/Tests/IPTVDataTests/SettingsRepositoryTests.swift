import XCTest
@testable import IPTVCore
@testable import IPTVData

final class SettingsRepositoryTests: XCTestCase {

    func testDefaultSettings() async {
        let repo = InMemorySettingsRepository()
        let settings = await repo.getSettings()

        XCTAssertTrue(settings.showChannelLogos)
        XCTAssertTrue(settings.enablePagination)
        XCTAssertEqual(settings.pageSize, 50)
        XCTAssertTrue(settings.showEpgInList)
        XCTAssertFalse(settings.autoLoadLastPlaylist)
        XCTAssertEqual(settings.bufferDurationSeconds, 15)
        XCTAssertEqual(settings.bufferStorageLimitMb, 1024)
        XCTAssertTrue(settings.keepScreenOn)
        XCTAssertTrue(settings.fastChannelSwitching)
        XCTAssertTrue(settings.hardwareAcceleration)
        XCTAssertEqual(settings.defaultAspectRatio, .fit)
        XCTAssertFalse(settings.showStreamInfoOverlay)
    }

    func testUpdateSettings() async {
        let repo = InMemorySettingsRepository()

        await repo.updateSettings { current in
            var updated = current
            updated.showChannelLogos = false
            updated.pageSize = 100
            updated.bufferDurationSeconds = 30
            updated.bufferStorageLimitMb = 2048
            updated.defaultAspectRatio = .zoom
            updated.showStreamInfoOverlay = true
            return updated
        }

        let updated = await repo.getSettings()
        XCTAssertFalse(updated.showChannelLogos)
        XCTAssertEqual(updated.pageSize, 100)
        XCTAssertEqual(updated.bufferDurationSeconds, 30)
        XCTAssertEqual(updated.bufferStorageLimitMb, 2048)
        XCTAssertEqual(updated.defaultAspectRatio, .zoom)
        XCTAssertTrue(updated.showStreamInfoOverlay)
    }

    func testResetToDefaults() async {
        let repo = InMemorySettingsRepository()

        await repo.updateSettings { current in
            var updated = current
            updated.showChannelLogos = false
            updated.bufferDurationSeconds = 60
            updated.enablePagination = false
            return updated
        }

        let updated = await repo.getSettings()
        XCTAssertFalse(updated.showChannelLogos)
        XCTAssertFalse(updated.enablePagination)

        await repo.resetToDefaults()

        let reset = await repo.getSettings()
        XCTAssertTrue(reset.showChannelLogos)
        XCTAssertTrue(reset.enablePagination)
        XCTAssertEqual(reset.bufferDurationSeconds, 15)
    }
}
