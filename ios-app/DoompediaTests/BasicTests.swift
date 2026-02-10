import XCTest
@testable import Doompedia

final class BasicTests: XCTestCase {
    func testNormalizeSearchCompactsWhitespaceAndCase() {
        XCTAssertEqual(normalizeSearch("  Alan   Turing "), "alan turing")
    }

    func testEditDistanceAtMostOne() {
        XCTAssertTrue(editDistanceAtMostOne("science", "sciense"))
        XCTAssertTrue(editDistanceAtMostOne("history", "history"))
        XCTAssertFalse(editDistanceAtMostOne("technology", "biology"))
    }
}
