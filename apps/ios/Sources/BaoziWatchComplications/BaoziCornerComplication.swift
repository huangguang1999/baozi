import SwiftUI
import WidgetKit

/// Corner (bottom-right) graphic complication. Ginger arc follows the corner
/// curve, with runtime + task title stacked at the inside edge.
struct BaoziCornerComplication: Widget {
    let kind = "BaoziCornerComplication"

    var body: some WidgetConfiguration {
        AppIntentConfiguration(
            kind: kind,
            intent: ServerSelectionIntent.self,
            provider: BaoziComplicationProvider()
        ) { entry in
            BaoziCornerView(entry: entry)
                .widgetAccentable()
                .containerBackground(.clear, for: .widget)
        }
        .supportedFamilies([.accessoryCorner])
        .configurationDisplayName("Codex Corner")
        .description("Task runtime in a corner slot with the task title underneath.")
    }
}

struct BaoziCornerView: View {
    let entry: BaoziComplicationEntry

    var body: some View {
        Text(entry.runtimeLabel(at: entry.date))
            .font(.system(size: 14, weight: .bold, design: .monospaced))
            .foregroundStyle(.white)
            .widgetCurvesContent()
            .widgetLabel {
                Text(shortTitle)
                    .font(.system(size: 11, weight: .medium, design: .monospaced))
                    .foregroundStyle(BaoziComplicationTint.ginger)
            }
            .widgetURL(entry.taskId.flatMap { URL(string: "baozi-watch://task/\($0)") })
    }

    private var shortTitle: String {
        let limit = 20
        return entry.title.count > limit
            ? String(entry.title.prefix(limit - 1)) + "…"
            : entry.title
    }
}

#Preview(as: .accessoryCorner) {
    BaoziCornerComplication()
} timeline: {
    BaoziComplicationEntry.placeholder
}
