import SwiftUI
import WidgetKit

/// Modular rectangular complication (Infograph Modular hero slot). Shows
/// the L badge + runtime header, full task title, and current tool call.
struct BaoziRectangularComplication: Widget {
    let kind = "BaoziRectangularComplication"

    var body: some WidgetConfiguration {
        AppIntentConfiguration(
            kind: kind,
            intent: ServerSelectionIntent.self,
            provider: BaoziComplicationProvider()
        ) { entry in
            BaoziRectangularView(entry: entry)
                .widgetAccentable()
                .containerBackground(.clear, for: .widget)
        }
        .supportedFamilies([.accessoryRectangular])
        .configurationDisplayName("Codex Modular")
        .description("Full task summary: title + current tool call.")
    }
}

struct BaoziRectangularView: View {
    let entry: BaoziComplicationEntry

    var body: some View {
        VStack(alignment: .leading, spacing: 2) {
            HStack(spacing: 4) {
                RoundedRectangle(cornerRadius: 3)
                    .fill(BaoziComplicationTint.ginger)
                    .frame(width: 10, height: 10)
                    .overlay(
                        Text("L")
                            .font(.system(size: 7, weight: .bold, design: .monospaced))
                            .foregroundStyle(.black)
                    )
                Text(eyebrow)
                    .font(.system(size: 8, weight: .bold, design: .monospaced))
                    .tracking(1.0)
                    .foregroundStyle(BaoziComplicationTint.ginger)
                Spacer(minLength: 0)
                if entry.mode == .running {
                    Circle()
                        .fill(BaoziComplicationTint.ginger)
                        .frame(width: 4, height: 4)
                }
            }
            Text(entry.title)
                .font(.system(size: 12, weight: .bold, design: .monospaced))
                .foregroundStyle(.white)
                .lineLimit(1)
                .truncationMode(.tail)
            Text(entry.toolLine)
                .font(.system(size: 9, design: .monospaced))
                .foregroundStyle(.white.opacity(0.6))
                .lineLimit(1)
                .truncationMode(.tail)
        }
        .widgetURL(entry.taskId.flatMap { URL(string: "baozi-watch://task/\($0)") })
    }

    private var eyebrow: String {
        entry.mode == .running
            ? "CODEX · \(entry.runtimeLabel(at: entry.date))"
            : "CODEX · \(entry.serverCount) READY"
    }
}

#Preview(as: .accessoryRectangular) {
    BaoziRectangularComplication()
} timeline: {
    BaoziComplicationEntry.placeholder
}
