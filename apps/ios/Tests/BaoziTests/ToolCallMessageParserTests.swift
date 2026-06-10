import XCTest
@testable import Baozi

final class ToolCallMessageParserTests: XCTestCase {
    func testParsesAllToolKinds() {
        let fixtures: [(String, ToolCallKind)] = [
            ("### Command Execution\nStatus: completed\n\nCommand:\n```bash\necho hello\n```", .commandExecution),
            ("### Command Output\n```text\nchunk\n```", .commandOutput),
            ("### File Change\nStatus: completed\n\nPath: /tmp/a.txt\nKind: update\n\n```diff\n@@ -1 +1 @@\n-a\n+b\n```", .fileChange),
            ("### File Diff\n```diff\n@@ -1 +1 @@\n-a\n+b\n```", .fileDiff),
            ("### MCP Tool Call\nStatus: completed\nTool: web/search", .mcpToolCall),
            ("### MCP Tool Progress\nIndexing workspace", .mcpToolProgress),
            ("### Web Search\nQuery: codex parser", .webSearch),
            ("### Collaboration\nStatus: inProgress\nTool: ask_agent", .collaboration),
            ("### Image View\nPath: /tmp/screenshot.png", .imageView)
        ]

        for (text, expectedKind) in fixtures {
            XCTAssertEqual(parseFirst(text).kind, expectedKind)
        }
    }

    func testWebSearchWithoutStatusDefaultsToCompleted() {
        let model = parseFirst(
            """
            ### Web Search
            Query: codex parser
            """
        )

        XCTAssertEqual(model.kind, .webSearch)
        XCTAssertEqual(model.status, .completed)
        XCTAssertEqual(model.summary, "codex parser")
    }

    func testMalformedFenceFallsBackToTextSection() {
        let model = parseFirst(
            """
            ### Command Output
            Output:
            ```text
            partial line
            """
        )

        XCTAssertEqual(model.kind, .commandOutput)
        XCTAssertTrue(model.sections.contains { section in
            if case .text(let label, _) = section {
                return label == "Output"
            }
            return false
        })
    }

    func testMissingHeadingReturnsNoCards() {
        let cards = MessageContentBridge.parseToolCalls(
            text: """
            Command Execution
            Status: completed
            """
        )

        XCTAssertTrue(cards.isEmpty)
    }

    func testFileChangeMultipleEntriesParsesRepeatedSections() {
        let model = parseFirst(
            """
            ### File Change
            Status: completed

            Path: /tmp/a.txt
            Kind: update

            ```diff
            @@ -1 +1 @@
            -a
            +b
            ```

            ---

            Path: /tmp/b.txt
            Kind: delete

            ```text
            old content
            ```
            """
        )

        XCTAssertEqual(model.summary, "a.txt +1 files")
        let changeMetadataCount = model.sections.filter {
            if case .kv(let label, _) = $0 {
                return label.hasPrefix("Change ")
            }
            return false
        }.count
        XCTAssertEqual(changeMetadataCount, 2)
    }

    func testMcpWithoutArgumentsStillRecognized() {
        let model = parseFirst(
            """
            ### MCP Tool Call
            Status: inProgress
            Tool: fs/read
            """
        )

        XCTAssertEqual(model.kind, .mcpToolCall)
        XCTAssertEqual(model.status, .inProgress)
        XCTAssertEqual(model.summary, "fs/read")
    }

    func testScalarAndInvalidJsonHandling() {
        let scalarModel = parseFirst(
            """
            ### Web Search
            Query: numbers

            Action:
            42
            """
        )
        XCTAssertTrue(scalarModel.sections.contains { section in
            if case .json(let label, let content) = section {
                return label == "Action" && content == "42"
            }
            return false
        })

        let invalidModel = parseFirst(
            """
            ### MCP Tool Call
            Status: completed
            Tool: server/tool

            Result:
            { this is not valid json
            """
        )
        XCTAssertTrue(invalidModel.sections.contains { section in
            if case .text(let label, _) = section {
                return label == "Result"
            }
            return false
        })
    }

    func testFailedCardsDefaultExpandedAndSectionOrder() throws {
        let model = parseFirst(
            """
            ### Command Execution
            Status: failed
            Duration: 12 ms
            Directory: /tmp

            Command:
            ```bash
            ls
            ```

            Output:
            ```text
            nope
            ```

            Progress:
            step one
            """
        )

        XCTAssertEqual(model.status, .failed)
        XCTAssertTrue(model.defaultExpanded)
        XCTAssertEqual(model.commandContext?.command, "ls")
        XCTAssertEqual(model.commandContext?.directory, "/tmp")

        let labels = model.sections.compactMap(sectionLabel)
        let outputIndex = try XCTUnwrap(labels.firstIndex(of: "Output"))
        let progressIndex = try XCTUnwrap(labels.firstIndex(of: "Progress"))

        XCTAssertEqual(labels.first, "Metadata")
        XCTAssertLessThan(outputIndex, progressIndex)
    }

    func testCollaborationTargetsParseIntoListSection() {
        let model = parseFirst(
            """
            ### Collaboration
            Status: completed
            Tool: ask_agent
            Targets: thread-alpha, agent-beta, unknown-id
            """
        )

        let targets = model.sections.compactMap { section -> [String]? in
            guard case .list(let label, let items) = section, label == "Targets" else { return nil }
            return items
        }.first

        XCTAssertEqual(targets, ["thread-alpha", "agent-beta", "unknown-id"])
    }

    func testCollaborationTargetBlockParsesIntoListSection() {
        let model = parseFirst(
            """
            ### Collaboration
            Status: completed
            Tool: spawnAgent

            Targets:
            - thread-alpha
            - agent-beta
            """
        )

        let targets = model.sections.compactMap { section -> [String]? in
            guard case .list(let label, let items) = section, label == "Targets" else { return nil }
            return items
        }.first

        XCTAssertEqual(targets, ["thread-alpha", "agent-beta"])
    }

    private func parseFirst(
        _ text: String,
        file: StaticString = #filePath,
        line: UInt = #line
    ) -> ToolCallCardModel {
        let cards = MessageContentBridge.parseToolCalls(text: text)
        guard let model = cards.first else {
            XCTFail("Expected at least one parsed tool call", file: file, line: line)
            return ToolCallCardModel(
                kind: .commandExecution,
                title: "",
                summary: "",
                status: .unknown,
                duration: nil,
                sections: []
            )
        }
        return model
    }

    private func sectionLabel(_ section: ToolCallSection) -> String? {
        switch section {
        case .kv(let label, _): return label
        case .code(let label, _, _): return label
        case .json(let label, _): return label
        case .diff(let label, _): return label
        case .text(let label, _): return label
        case .list(let label, _): return label
        case .progress(let label, _): return label
        }
    }
}
