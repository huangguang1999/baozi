import XCTest
@testable import Baozi

@MainActor
final class ConversationPlanSemanticsTests: XCTestCase {
    func testProposedPlanPreservesMarkdownContent() {
        let item = ConversationItem(
            id: "plan-1",
            content: .proposedPlan(
                ConversationProposedPlanData(
                    content: "# Final plan\n- first\n- second\n"
                )
            )
        )

        guard case .proposedPlan(let data) = item.content else {
            return XCTFail("Expected proposed plan content")
        }
        XCTAssertEqual(data.content, "# Final plan\n- first\n- second\n")
    }

    func testTodoListTracksCompletionState() {
        let data = ConversationTodoListData(
            steps: [
                ConversationPlanStep(step: "Inspect renderer", status: .completed),
                ConversationPlanStep(step: "Patch iOS client", status: .inProgress),
            ]
        )

        XCTAssertEqual(data.completedCount, 1)
        XCTAssertFalse(data.isComplete)
    }
}
