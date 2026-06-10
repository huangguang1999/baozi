import SwiftUI
import UIKit
import XCTest
@testable import Baozi

final class BaoziAppearanceModeTests: XCTestCase {
    func testPreferredColorSchemeMapping() {
        XCTAssertNil(BaoziAppearanceMode.system.preferredColorScheme)
        XCTAssertEqual(BaoziAppearanceMode.light.preferredColorScheme, .light)
        XCTAssertEqual(BaoziAppearanceMode.dark.preferredColorScheme, .dark)
    }

    func testResolvedColorSchemeUsesSystemOnlyForSystemMode() {
        XCTAssertEqual(BaoziAppearanceMode.system.resolvedColorScheme(systemColorScheme: .light), .light)
        XCTAssertEqual(BaoziAppearanceMode.system.resolvedColorScheme(systemColorScheme: .dark), .dark)
        XCTAssertEqual(BaoziAppearanceMode.light.resolvedColorScheme(systemColorScheme: .dark), .light)
        XCTAssertEqual(BaoziAppearanceMode.dark.resolvedColorScheme(systemColorScheme: .light), .dark)
    }

    func testUserInterfaceStyleMapping() {
        XCTAssertEqual(BaoziAppearanceMode.system.userInterfaceStyle, .unspecified)
        XCTAssertEqual(BaoziAppearanceMode.light.userInterfaceStyle, .light)
        XCTAssertEqual(BaoziAppearanceMode.dark.userInterfaceStyle, .dark)
    }
}
