import XCTest
@testable import IPTVPlayer

final class TimeShiftBufferControllerTests: XCTestCase {

    private let controller = TimeShiftBufferController()

    func testUpdateProgressAtLiveEdge() {
        // Position equals session duration -> at live edge
        let state = controller.updateProgress(sessionDurationMs: 60000, positionFromStartMs: 60000)

        XCTAssertTrue(state.isAtLiveEdge)
        XCTAssertEqual(state.timeShiftOffsetMs, 0)
        XCTAssertTrue(state.canGoBackToStart)
    }

    func testUpdateProgressWithinToleranceAtLiveEdge() {
        // Offset 2000ms <= 3000ms tolerance -> still at live edge
        let state = controller.updateProgress(sessionDurationMs: 60000, positionFromStartMs: 58000)

        XCTAssertTrue(state.isAtLiveEdge)
        XCTAssertEqual(state.timeShiftOffsetMs, 2000)
        XCTAssertTrue(state.canGoBackToStart)
    }

    func testUpdateProgressRewoundBehindLiveEdge() {
        // Offset 10000ms > 3000ms -> not at live edge
        let state = controller.updateProgress(sessionDurationMs: 60000, positionFromStartMs: 50000)

        XCTAssertFalse(state.isAtLiveEdge)
        XCTAssertEqual(state.timeShiftOffsetMs, 10000)
        XCTAssertTrue(state.canGoBackToStart)
    }

    func testCannotGoBackWhenSessionTooShort() {
        // Session under 5 seconds
        let state = controller.updateProgress(sessionDurationMs: 3000, positionFromStartMs: 3000)
        XCTAssertFalse(state.canGoBackToStart)
    }

    func testResetLiveBuffer() {
        let state = controller.resetLiveBuffer()

        XCTAssertEqual(state.liveSessionDurationMs, 0)
        XCTAssertEqual(state.livePositionFromStartMs, 0)
        XCTAssertTrue(state.isAtLiveEdge)
        XCTAssertFalse(state.canGoBackToStart)
        XCTAssertTrue(state.liveSessionStartTimeMs > 0)
    }
}
