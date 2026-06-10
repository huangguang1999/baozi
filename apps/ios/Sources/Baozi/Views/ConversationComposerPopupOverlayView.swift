import SwiftUI

enum ConversationComposerPopupState {
    case none
    case slash([ComposerSlashCommand])
    case file(
        loading: Bool,
        error: String?,
        suggestions: [FileSearchResult],
        plugins: [PluginSummary]
    )
    case skill(loading: Bool, suggestions: [SkillMetadata])
}

struct ConversationComposerPopupOverlayView: View {
    let state: ConversationComposerPopupState
    let onApplySlashSuggestion: (ComposerSlashCommand) -> Void
    let onApplyFileSuggestion: (FileSearchResult) -> Void
    let onApplySkillSuggestion: (SkillMetadata) -> Void
    let onApplyPluginSuggestion: (PluginSummary) -> Void

    var body: some View {
        switch state {
        case .none:
            EmptyView()

        case .slash(let suggestions):
            suggestionPopup {
                let indexedSuggestions = Array(suggestions.enumerated())
                ForEach(indexedSuggestions, id: \.offset) { item in
                    let index = item.offset
                    let command = item.element
                    VStack(spacing: 0) {
                        Button {
                            onApplySlashSuggestion(command)
                        } label: {
                            HStack(spacing: 10) {
                                Text("/\(command.rawValue)")
                                    .baoziFont(.body)
                                    .foregroundColor(BaoziTheme.success)
                                Text(command.description)
                                    .baoziFont(.body)
                                    .foregroundColor(BaoziTheme.textSecondary)
                                    .lineLimit(1)
                                Spacer(minLength: 0)
                            }
                            .padding(.horizontal, 12)
                            .padding(.vertical, 9)
                        }
                        .buttonStyle(.plain)

                        Divider()
                            .background(BaoziTheme.border)
                            .opacity(index < suggestions.count - 1 ? 1 : 0)
                    }
                }
            }

        case .file(let loading, let error, let suggestions, let plugins):
            suggestionPopup {
                let cappedPlugins = Array(plugins.prefix(6))
                let cappedFiles = Array(suggestions.prefix(8))
                if cappedPlugins.isEmpty && loading {
                    popupStateText("Searching files...")
                } else if cappedPlugins.isEmpty && cappedFiles.isEmpty {
                    if let error, !error.isEmpty {
                        popupStateText(error, color: .red)
                    } else {
                        popupStateText("No matches")
                    }
                } else {
                    if !cappedPlugins.isEmpty {
                        sectionHeader("Plugins")
                        let indexedPlugins = Array(cappedPlugins.enumerated())
                        ForEach(indexedPlugins, id: \.element.id) { item in
                            let index = item.offset
                            let plugin = item.element
                            VStack(spacing: 0) {
                                Button {
                                    onApplyPluginSuggestion(plugin)
                                } label: {
                                    HStack(spacing: 8) {
                                        Image(systemName: "puzzlepiece.extension.fill")
                                            .baoziFont(.caption)
                                            .foregroundColor(BaoziTheme.accent)
                                        VStack(alignment: .leading, spacing: 2) {
                                            Text(plugin.displayTitle)
                                                .baoziFont(.footnote)
                                                .foregroundColor(BaoziTheme.textPrimary)
                                                .lineLimit(1)
                                            if let subtitle = plugin.interface?.shortDescription, !subtitle.isEmpty {
                                                Text(subtitle)
                                                    .baoziFont(.caption)
                                                    .foregroundColor(BaoziTheme.textSecondary)
                                                    .lineLimit(1)
                                            }
                                        }
                                        Spacer(minLength: 0)
                                    }
                                    .padding(.horizontal, 12)
                                    .padding(.vertical, 9)
                                }
                                .buttonStyle(.plain)

                                Divider()
                                    .background(BaoziTheme.border)
                                    .opacity(index < indexedPlugins.count - 1 || !cappedFiles.isEmpty ? 1 : 0)
                            }
                        }
                    }

                    if !cappedFiles.isEmpty {
                        if !cappedPlugins.isEmpty {
                            sectionHeader("Files")
                        }
                        let indexedSuggestions = Array(cappedFiles.enumerated())
                        ForEach(indexedSuggestions, id: \.offset) { item in
                            let index = item.offset
                            let suggestion = item.element
                            VStack(spacing: 0) {
                                Button {
                                    onApplyFileSuggestion(suggestion)
                                } label: {
                                    HStack(spacing: 8) {
                                        Image(systemName: "folder")
                                            .baoziFont(.caption)
                                            .foregroundColor(BaoziTheme.textSecondary)
                                        Text(suggestion.path)
                                            .baoziFont(.footnote)
                                            .foregroundColor(BaoziTheme.textPrimary)
                                            .lineLimit(1)
                                        Spacer(minLength: 0)
                                    }
                                    .padding(.horizontal, 12)
                                    .padding(.vertical, 9)
                                }
                                .buttonStyle(.plain)

                                Divider()
                                    .background(BaoziTheme.border)
                                    .opacity(index < indexedSuggestions.count - 1 ? 1 : 0)
                            }
                        }
                    }
                }
            }

        case .skill(let loading, let suggestions):
            suggestionPopup {
                if loading && suggestions.isEmpty {
                    popupStateText("Loading skills...")
                } else if suggestions.isEmpty {
                    popupStateText("No skills found")
                } else {
                    let indexedSuggestions = Array(Array(suggestions.prefix(8)).enumerated())
                    ForEach(indexedSuggestions, id: \.offset) { item in
                        let index = item.offset
                        let skill = item.element
                        VStack(spacing: 0) {
                            Button {
                                onApplySkillSuggestion(skill)
                            } label: {
                                HStack(spacing: 8) {
                                    Text("$\(skill.name)")
                                        .baoziFont(.footnote)
                                        .foregroundColor(BaoziTheme.success)
                                    Text(skill.description)
                                        .baoziFont(.footnote)
                                        .foregroundColor(BaoziTheme.textSecondary)
                                        .lineLimit(1)
                                    Spacer(minLength: 0)
                                }
                                .padding(.horizontal, 12)
                                .padding(.vertical, 9)
                            }
                            .buttonStyle(.plain)

                            Divider()
                                .background(BaoziTheme.border)
                                .opacity(index < indexedSuggestions.count - 1 ? 1 : 0)
                        }
                    }
                }
            }
        }
    }

    @ViewBuilder
    private func sectionHeader(_ title: String) -> some View {
        Text(title)
            .baoziFont(.caption)
            .foregroundColor(BaoziTheme.textSecondary)
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(.horizontal, 12)
            .padding(.top, 6)
            .padding(.bottom, 4)
    }

    @ViewBuilder
    private func popupStateText(_ text: String, color: Color = BaoziTheme.textSecondary) -> some View {
        Text(text)
            .baoziFont(.footnote)
            .foregroundColor(color)
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(.horizontal, 12)
            .padding(.vertical, 10)
    }

    @ViewBuilder
    private func suggestionPopup<Content: View>(@ViewBuilder content: () -> Content) -> some View {
        VStack(spacing: 0) {
            content()
        }
        .frame(maxWidth: .infinity)
        .background(BaoziTheme.surface.opacity(0.95))
        .overlay(
            RoundedRectangle(cornerRadius: 8)
                .stroke(BaoziTheme.border, lineWidth: 1)
        )
        .clipShape(RoundedRectangle(cornerRadius: 8))
        .padding(.horizontal, 12)
        .padding(.bottom, 4)
        .padding(.bottom, 56)
    }
}
