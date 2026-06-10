import SwiftUI

struct CodeBlockView: View {
    let language: String
    let code: String
    var fontSize: CGFloat = BaoziFont.conversationBodyPointSize

    var body: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            if isDiffLanguage(language) {
                SyntaxHighlightedDiffText(
                    diff: code,
                    titleHint: language.isEmpty ? nil : language,
                    fontSize: BaoziFont.conversationDiffPointSize
                )
                .padding(12)
                .frame(maxWidth: .infinity, alignment: .leading)
            } else {
                Text(code)
                    .baoziMonoFont(size: fontSize)
                    .foregroundColor(BaoziTheme.textBody)
                    .textSelection(.enabled)
                    .padding(12)
                    .frame(maxWidth: .infinity, alignment: .leading)
            }
        }
        .background(BaoziTheme.codeBackground.opacity(0.8))
        .clipShape(RoundedRectangle(cornerRadius: 8))
        .modifier(GlassRectModifier(cornerRadius: 8))
    }
}

#if DEBUG
#Preview("Code Block") {
    ZStack {
        BaoziTheme.backgroundGradient.ignoresSafeArea()
        CodeBlockView(
            language: "swift",
            code: """
            struct SchedulerGate {
                let repoJobs = 100_000

                func canEnqueue(_ pending: Int) -> Bool {
                    pending < repoJobs
                }
            }
            """
        )
        .padding(20)
    }
}
#endif
