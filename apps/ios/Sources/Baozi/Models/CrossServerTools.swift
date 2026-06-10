import Foundation

enum CrossServerTools {
    static let listServersToolName = "list_servers"
    static let listSessionsToolName = "list_sessions"

    /// Build the dynamic tool specs for cross-server operations.
    static func buildDynamicToolSpecs() -> [DynamicToolSpecParams] {
        [
            listServersSpec(),
            listSessionsSpec()
        ]
    }

    /// Returns true if the given tool name is a cross-server tool that
    /// should be rendered with rich formatting in the conversation timeline.
    static func isRichTool(_ toolName: String) -> Bool {
        switch toolName {
        case listServersToolName, listSessionsToolName:
            return true
        default:
            return false
        }
    }

    private static func listServersSpec() -> DynamicToolSpecParams {
        DynamicToolSpecParams(
            name: listServersToolName,
            description: "List all connected servers and their status. After calling this tool, briefly tell the user what you found.",
            inputSchema: AnyEncodable(JSONSchema.object([:], required: []))
        )
    }

    private static func listSessionsSpec() -> DynamicToolSpecParams {
        DynamicToolSpecParams(
            name: listSessionsToolName,
            description: "List recent sessions/threads on a specific server or all connected servers. After calling this tool, briefly tell the user what you found.",
            inputSchema: AnyEncodable(JSONSchema.object([
                "server": .string(description: "Server name to query. Omit to query all connected servers.")
            ], required: []))
        )
    }
}
