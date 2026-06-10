import XCTest
@testable import Baozi

final class ConversationScreenModelTests: XCTestCase {
    @MainActor
    func testProjectionCacheKeepsLatestProjectedItemsAcrossSamePayloadRebind() {
        let model = ConversationScreenModel()

        let initialItems = [makeHydratedAssistantItem(id: "item-1", text: "Hel")]
        let streamedItems = [makeHydratedAssistantItem(id: "item-1", text: "Hello")]

        _ = model._testProjectConversationItems(from: initialItems)
        let streamedProjection = model._testProjectConversationItems(from: streamedItems)
        let replayProjection = model._testProjectConversationItems(from: streamedItems)

        XCTAssertEqual(streamedProjection.count, 1)
        XCTAssertEqual(replayProjection.count, 1)
        XCTAssertEqual(streamedProjection[0].assistantText, "Hello")
        XCTAssertEqual(replayProjection[0].assistantText, "Hello")
    }
}

private func makeHydratedAssistantItem(id: String, text: String) -> HydratedConversationItem {
    HydratedConversationItem(
        id: id,
        content: .assistant(
            HydratedAssistantMessageData(
                text: text,
                agentNickname: nil,
                agentRole: nil,
                phase: nil
            )
        ),
        sourceTurnId: "turn-1",
        sourceTurnIndex: 0,
        timestamp: 1,
        isFromUserTurnBoundary: false
    )
}
