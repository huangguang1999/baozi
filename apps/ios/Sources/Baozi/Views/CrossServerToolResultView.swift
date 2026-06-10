import SwiftUI

/// Rich rendering for list_servers and list_sessions tool results.
/// Decodes structured JSON from contentSummary and renders using the same
/// visual style as the home page server/session cards.
struct CrossServerToolResultView: View {
    let data: ConversationDynamicToolCallData

    var body: some View {
        if let payload = decode() {
            VStack(alignment: .leading, spacing: 6) {
                switch payload {
                case .servers(let items):
                    if items.isEmpty {
                        emptyRow("No servers.")
                    } else {
                        ForEach(items, id: \.name) { s in
                            SessionServerCardRow(
                                icon: s.isLocal ? "iphone" : "server.rack",
                                title: s.name,
                                subtitle: s.hostname,
                                trailing: .status(connected: s.isConnected)
                            )
                        }
                    }
                case .sessions(let items):
                    if items.isEmpty {
                        emptyRow("No sessions.")
                    } else {
                        ForEach(items, id: \.threadId) { s in
                            SessionServerCardRow(
                                icon: "text.bubble",
                                title: s.title,
                                subtitle: [s.serverName, s.model.isEmpty ? nil : s.model].compactMap { $0 }.joined(separator: " · "),
                                trailing: .none
                            )
                        }
                    }
                }
            }
        }
    }

    private func emptyRow(_ text: String) -> some View {
        Text(text).baoziFont(.caption).foregroundColor(BaoziTheme.textMuted).padding(.vertical, 4)
    }

    // MARK: - Decoding

    private struct ServerItem: Decodable {
        let name: String
        let hostname: String
        let isConnected: Bool
        let isLocal: Bool
    }

    private struct SessionItem: Decodable {
        // From ThreadSummary (server response)
        let id: String
        let preview: String?
        let modelProvider: String?
        let updatedAt: Int64?
        let cwd: String?
        // Added by our handler
        let serverName: String?

        var threadId: String { id }
        var title: String {
            let t = (preview ?? "").trimmingCharacters(in: .whitespacesAndNewlines)
            return t.isEmpty ? "Untitled session" : t
        }
        var model: String { modelProvider ?? "" }
        var parsedDate: Date? {
            updatedAt.map { Date(timeIntervalSince1970: TimeInterval($0) / 1000.0) }
        }

        private enum CodingKeys: String, CodingKey {
            case id, preview, modelProvider, updatedAt, cwd, serverName
            case modelProviderSnake = "model_provider"
            case updatedAtSnake = "updated_at"
        }

        init(from decoder: Decoder) throws {
            let c = try decoder.container(keyedBy: CodingKeys.self)
            id = try c.decode(String.self, forKey: .id)
            preview = try c.decodeIfPresent(String.self, forKey: .preview)
            modelProvider = try c.decodeIfPresent(String.self, forKey: .modelProvider)
                ?? c.decodeIfPresent(String.self, forKey: .modelProviderSnake)
            updatedAt = try c.decodeIfPresent(Int64.self, forKey: .updatedAt)
                ?? c.decodeIfPresent(Int64.self, forKey: .updatedAtSnake)
            cwd = try c.decodeIfPresent(String.self, forKey: .cwd)
            serverName = try c.decodeIfPresent(String.self, forKey: .serverName)
        }
    }

    private enum DecodedPayload {
        case servers([ServerItem])
        case sessions([SessionItem])
    }

    private func decode() -> DecodedPayload? {
        guard let summary = data.contentSummary,
              let jsonData = summary.data(using: .utf8),
              let obj = try? JSONSerialization.jsonObject(with: jsonData) as? [String: Any],
              let type = obj["type"] as? String,
              let itemsData = try? JSONSerialization.data(withJSONObject: obj["items"] ?? []) else {
            return nil
        }
        switch type {
        case "servers":
            return (try? JSONDecoder().decode([ServerItem].self, from: itemsData)).map { .servers($0) }
        case "sessions":
            return (try? JSONDecoder().decode([SessionItem].self, from: itemsData)).map { .sessions($0) }
        default:
            return nil
        }
    }
}

// MARK: - Shared card row used by both tool results and home page

/// A reusable card row matching the home page server/session visual style.
struct SessionServerCardRow: View {
    let icon: String
    let title: String
    let subtitle: String
    var trailing: Trailing = .none

    enum Trailing {
        case none
        case status(connected: Bool)
        case statusLabel(String, Color)
        case badge(String)
        case chevron
    }

    var body: some View {
        HStack(alignment: .center, spacing: 12) {
            Image(systemName: icon)
                .baoziFont(size: 16, weight: .medium)
                .foregroundColor(BaoziTheme.accent)
                .frame(width: 28, height: 28)
                .background(BaoziTheme.accent.opacity(0.12))
                .clipShape(RoundedRectangle(cornerRadius: 8))

            VStack(alignment: .leading, spacing: 4) {
                Text(title)
                    .baoziFont(.subheadline)
                    .foregroundColor(BaoziTheme.textPrimary)
                    .lineLimit(1)

                if !subtitle.isEmpty {
                    Text(subtitle)
                        .baoziFont(.caption)
                        .foregroundColor(BaoziTheme.textMuted)
                        .lineLimit(1)
                }
            }

            Spacer(minLength: 0)

            switch trailing {
            case .none:
                EmptyView()
            case .status(let connected):
                HStack(spacing: 6) {
                    Circle()
                        .fill(connected ? BaoziTheme.accent : BaoziTheme.textMuted.opacity(0.5))
                        .frame(width: 8, height: 8)
                    Text(connected ? "Connected" : "Offline")
                        .baoziFont(.caption)
                        .foregroundColor(BaoziTheme.textMuted)
                }
            case .statusLabel(let label, let color):
                HStack(spacing: 6) {
                    Circle()
                        .fill(color)
                        .frame(width: 8, height: 8)
                    Text(label)
                        .baoziFont(.caption)
                        .foregroundColor(BaoziTheme.textMuted)
                }
            case .badge(let text):
                Text(text)
                    .baoziFont(.caption, weight: .semibold)
                    .foregroundColor(BaoziTheme.accent)
            case .chevron:
                Image(systemName: "chevron.right")
                    .baoziFont(size: 12, weight: .semibold)
                    .foregroundColor(BaoziTheme.textMuted)
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(.horizontal, 14)
        .padding(.vertical, 12)
        .background(BaoziTheme.surface.opacity(0.6))
        .overlay(
            RoundedRectangle(cornerRadius: 14)
                .stroke(BaoziTheme.border.opacity(0.7), lineWidth: 1)
        )
        .clipShape(RoundedRectangle(cornerRadius: 14))
    }
}
