import SwiftUI

struct VoiceCallLockScreenCardView: View {
    let attributes: CodexVoiceCallAttributes
    let state: CodexVoiceCallAttributes.ContentState

    @Environment(\.colorScheme) private var colorScheme

    private var cardBackground: Color { BaoziPalette.surface.color(for: colorScheme) }
    private var logoBackground: Color { BaoziPalette.surfaceLight.color(for: colorScheme) }
    private var primaryText: Color { BaoziPalette.textPrimary.color(for: colorScheme) }
    private var secondaryText: Color { BaoziPalette.textSecondary.color(for: colorScheme) }
    private var tertiaryText: Color { BaoziPalette.textMuted.color(for: colorScheme) }

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            HStack(spacing: 10) {
                Image("brand_logo")
                    .resizable()
                    .interpolation(.high)
                    .scaledToFit()
                    .frame(width: 24, height: 24)
                    .padding(2)
                    .background(
                        RoundedRectangle(cornerRadius: 7, style: .continuous)
                            .fill(logoBackground)
                    )

                VStack(alignment: .leading, spacing: 3) {
                    Text(attributes.threadTitle)
                        .font(.system(size: 13, weight: .medium, design: .monospaced))
                        .foregroundStyle(primaryText)
                        .lineLimit(1)
                    Text(statusText)
                        .font(.system(size: 11, design: .monospaced))
                        .foregroundStyle(phaseColor.opacity(0.8))
                        .lineLimit(1)
                }

                Spacer(minLength: 0)

                Text(timerInterval: attributes.startDate...Date.distantFuture, countsDown: false)
                    .font(.system(size: 15, design: .monospaced))
                    .foregroundStyle(tertiaryText)
                    .monospacedDigit()
            }

            if let transcript = state.transcriptText, !transcript.isEmpty {
                Text(transcript)
                    .font(.system(size: 11, design: .monospaced))
                    .foregroundStyle(secondaryText)
                    .lineLimit(2)
                    .fixedSize(horizontal: false, vertical: true)
                    .padding(.top, 5)
                    .padding(.leading, 38)
            }

            HStack(spacing: 8) {
                phaseBadge
                Label(state.routeLabel, systemImage: routeIcon)
                    .font(.system(size: 10, design: .monospaced))
                    .foregroundStyle(tertiaryText)
                Spacer()
                Button(intent: EndVoiceSessionIntent()) {
                    Image(systemName: "xmark.circle.fill")
                        .font(.system(size: 13, weight: .semibold))
                        .foregroundStyle(.white)
                        .padding(8)
                        .background(Circle().fill(BaoziPalette.danger.color(for: colorScheme)))
                }
                .buttonStyle(.plain)
            }
            .padding(.top, 10)
            .padding(.leading, 38)
        }
        .padding(.horizontal, 14)
        .padding(.vertical, 12)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(cardBackground)
    }

    private var statusText: String {
        if let lastError = state.lastError, !lastError.isEmpty {
            return lastError
        }
        switch state.phase {
        case .connecting: return "Connecting"
        case .listening: return "Listening"
        case .thinking: return "Thinking"
        case .speaking: return "Codex speaking"
        case .error: return "Session ended"
        }
    }

    private var phaseColor: Color {
        switch state.phase {
        case .connecting, .thinking:
            return BaoziPalette.warning.color(for: colorScheme)
        case .listening, .speaking:
            return BaoziPalette.accent.color(for: colorScheme)
        case .error:
            return BaoziPalette.danger.color(for: colorScheme)
        }
    }

    private var phaseBadge: some View {
        Text(statusText.lowercased())
            .font(.system(size: 10, weight: .medium, design: .monospaced))
            .foregroundStyle(phaseColor)
            .padding(.horizontal, 7)
            .padding(.vertical, 2)
            .background(
                RoundedRectangle(cornerRadius: 4, style: .continuous)
                    .fill(phaseColor.opacity(0.12))
            )
    }

    private var routeIcon: String {
        let label = state.routeLabel.lowercased()
        if label.contains("speaker") {
            return "speaker.wave.3.fill"
        }
        if label.contains("head") {
            return "headphones"
        }
        if label.contains("bluetooth") {
            return "dot.radiowaves.left.and.right"
        }
        return "speaker.wave.2.fill"
    }
}
