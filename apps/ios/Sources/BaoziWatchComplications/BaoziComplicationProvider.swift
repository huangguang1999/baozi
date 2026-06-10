import Foundation
import WidgetKit

/// `AppIntentTimelineProvider` shared by all three complications. Resolves
/// the configured `ServerSelectionIntent.server`:
///
/// - `nil` → use the aggregate `complication.snapshot.v1` (legacy/default).
/// - non-nil → look up that server's slice in
///   `complication.per-server.v1` and fall back to the aggregate if the
///   selected server has no entry yet.
///
/// The legacy `TimelineProvider`-shape behavior (one entry now + 30 ticks
/// while running) is preserved unchanged.
struct BaoziComplicationProvider: AppIntentTimelineProvider {
    typealias Intent = ServerSelectionIntent
    typealias Entry = BaoziComplicationEntry

    func placeholder(in context: Context) -> BaoziComplicationEntry {
        .placeholder
    }

    func snapshot(for configuration: ServerSelectionIntent, in context: Context) async -> BaoziComplicationEntry {
        resolveCurrent(for: configuration)
    }

    func timeline(for configuration: ServerSelectionIntent, in context: Context) async -> Timeline<BaoziComplicationEntry> {
        let base = resolveCurrent(for: configuration)
        return makeTimeline(base: base)
    }

    func recommendations() -> [AppIntentRecommendation<ServerSelectionIntent>] {
        []
    }

    // MARK: - Resolution

    private func resolveCurrent(for configuration: ServerSelectionIntent) -> BaoziComplicationEntry {
        if let serverId = configuration.server?.id,
           let payload = perServerPayload(for: serverId) {
            return BaoziComplicationStore.entry(from: payload)
        }
        return BaoziComplicationStore.current()
    }

    private func perServerPayload(for serverId: String) -> BaoziComplicationPayload? {
        let map = BaoziPerServerComplicationStore.current()
        guard let data = map[serverId] else { return nil }
        return try? JSONDecoder().decode(BaoziComplicationPayload.self, from: data)
    }

    // MARK: - Timeline shape

    private func makeTimeline(base: BaoziComplicationEntry) -> Timeline<BaoziComplicationEntry> {
        let now = Date()
        var entries: [BaoziComplicationEntry] = []

        if base.mode == .running {
            // Tick once a minute for the next 30m. Each entry carries the same
            // start epoch so the view recomputes elapsed against `entry.date`.
            for step in 0..<30 {
                entries.append(
                    BaoziComplicationEntry(
                        date: now.addingTimeInterval(TimeInterval(step) * 60),
                        mode: .running,
                        lastTurnStartMsEpoch: base.lastTurnStartMsEpoch,
                        taskId: base.taskId,
                        progress: min(1, base.progress + Double(step) * 0.01),
                        title: base.title,
                        toolLine: base.toolLine,
                        serverCount: base.serverCount
                    )
                )
            }
            return Timeline(entries: entries, policy: .after(now.addingTimeInterval(60 * 30)))
        } else {
            entries.append(base)
            return Timeline(entries: entries, policy: .after(now.addingTimeInterval(60 * 15)))
        }
    }
}
