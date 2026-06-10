import XCTest
@testable import Baozi

final class PluginMentionEncodingTests: XCTestCase {
    func testMentionUserInputEncodesAsMentionType() throws {
        let input = AppUserInput.mention(
            name: "computer-use",
            path: "plugin://computer-use@openai-curated"
        )

        let data = try JSONEncoder().encode(input)
        let object = try JSONSerialization.jsonObject(with: data) as? [String: Any]
        let unwrapped = try XCTUnwrap(object)

        XCTAssertEqual(unwrapped["type"] as? String, "mention")
        XCTAssertEqual(unwrapped["name"] as? String, "computer-use")
        XCTAssertEqual(
            unwrapped["path"] as? String,
            "plugin://computer-use@openai-curated"
        )
        XCTAssertEqual(unwrapped.count, 3, "mention input should serialize exactly type/name/path")
    }

    func testPluginMentionSelectionExposesCanonicalPath() {
        let selection = PluginMentionSelection(
            name: "linear",
            marketplace: "community",
            displayName: "Linear"
        )

        XCTAssertEqual(selection.path, "plugin://linear@community")
        XCTAssertEqual(selection.displayTitle, "Linear")
    }

    func testPluginMentionSelectionFallsBackToNameWhenDisplayNameMissing() {
        let selection = PluginMentionSelection(
            name: "linear",
            marketplace: "community",
            displayName: nil
        )

        XCTAssertEqual(selection.displayTitle, "linear")
    }
}
