import SwiftUI

struct AccountView: View {
    @Environment(AppModel.self) private var appModel
    @Environment(\.dismiss) private var dismiss

    private var server: AppServerSnapshot? {
        // Account management (ChatGPT login / API key) is local-only, always.
        // If the local Codex bridge hasn't spun up there's no login target, and
        // the caller falls through to `AccountDisconnectedView`.
        appModel.snapshot?.servers.first(where: \.isLocal)
    }

    var body: some View {
        if let server {
            AccountConnectionView(server: server, dismiss: dismiss)
        } else {
            AccountDisconnectedView(dismiss: dismiss)
        }
    }
}

private struct AccountConnectionView: View {
    @Environment(AppModel.self) private var appModel
    let server: AppServerSnapshot
    let dismiss: DismissAction

    @State private var apiKey = ""
    @State private var isWorking = false
    @State private var authError: String?
    @State private var hasStoredApiKey = OpenAIApiKeyStore.shared.hasStoredKey

    var body: some View {
        NavigationStack {
            ZStack {
                BaoziTheme.backgroundGradient.ignoresSafeArea()
                ScrollView {
                    VStack(alignment: .leading, spacing: 24) {
                        currentAccountSection
                        Divider().background(BaoziTheme.surfaceLight)
                        loginSection
                        if let err = authError {
                            Text(err)
                                .font(.caption)
                                .foregroundColor(.red)
                                .padding(.horizontal, 20)
                        }
                    }
                    .padding(.top, 20)
                }
            }
            .navigationTitle("Account")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarTrailing) {
                    Button("Done") { dismiss() }
                        .foregroundColor(BaoziTheme.accent)
                }
            }
            .task(id: server.serverId) {
                await refreshAccount()
                hasStoredApiKey = OpenAIApiKeyStore.shared.hasStoredKey
            }
        }
    }

    private var currentAccountSection: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text("CURRENT ACCOUNT")
                .baoziFont(.caption)
                .foregroundColor(BaoziTheme.textMuted)
                .padding(.horizontal, 20)

            HStack(spacing: 12) {
                Circle()
                    .fill(authColor)
                    .frame(width: 10, height: 10)
                VStack(alignment: .leading, spacing: 2) {
                    Text(authTitle)
                        .baoziFont(.subheadline)
                        .foregroundColor(BaoziTheme.textPrimary)
                    if let sub = authSubtitle {
                        Text(sub)
                            .baoziFont(.caption)
                            .foregroundColor(BaoziTheme.textSecondary)
                    }
                }
                Spacer()
                if server.isLocal, server.account != nil {
                    Button("Logout") {
                        Task { await logout() }
                    }
                    .baoziFont(.footnote)
                    .foregroundColor(BaoziTheme.danger)
                }
            }
            .padding(.horizontal, 20)
            .padding(.vertical, 14)
            .background(.ultraThinMaterial)
            .cornerRadius(10)
            .padding(.horizontal, 16)

            if server.isLocal, hasStoredApiKey {
                Text("Local OpenAI API key is saved.")
                    .baoziFont(.caption)
                    .foregroundColor(BaoziTheme.accent)
                    .padding(.horizontal, 20)
            }
        }
    }

    private var loginSection: some View {
        VStack(alignment: .leading, spacing: 16) {
            Text("LOGIN")
                .baoziFont(.caption)
                .foregroundColor(BaoziTheme.textMuted)
                .padding(.horizontal, 20)

            // [baozi-fork] ChatGPT OAuth login removed: replaying a first-party
            // ChatGPT/Claude subscription login inside a third-party client risks
            // violating those services' terms. Bring your own API key instead.
            if server.isLocal, allowsLocalEnvApiKey {
                Text("Bring your own OpenAI API key. You are responsible for the key, any usage costs, and compliance with the provider's terms of service.")
                    .baoziFont(.caption)
                    .foregroundColor(BaoziTheme.textMuted)
                    .padding(.horizontal, 16)

                VStack(alignment: .leading, spacing: 8) {
                    if hasStoredApiKey {
                        Text("OpenAI API key saved in the local environment.")
                            .baoziFont(.caption)
                            .foregroundColor(BaoziTheme.textSecondary)
                            .padding(.horizontal, 16)
                    } else if isChatGPTAccount {
                        Text("Save an OpenAI API key in the local Codex environment.")
                            .baoziFont(.caption)
                            .foregroundColor(BaoziTheme.textSecondary)
                            .padding(.horizontal, 16)
                    }

                    SecureField("sk-...", text: $apiKey)
                        .baoziFont(.subheadline)
                        .foregroundColor(BaoziTheme.textPrimary)
                        .textInputAutocapitalization(.never)
                        .autocorrectionDisabled()
                        .padding(12)
                        .background(BaoziTheme.surface)
                        .cornerRadius(8)
                        .padding(.horizontal, 16)

                    Button {
                        let key = apiKey.trimmingCharacters(in: .whitespaces)
                        guard !key.isEmpty else { return }
                        Task {
                            isWorking = true
                            await saveApiKey(key)
                            isWorking = false
                        }
                    } label: {
                        Text(LocalizedStringKey(hasStoredApiKey ? "Update API Key" : "Save API Key"))
                            .baoziFont(.subheadline)
                            .foregroundColor(BaoziTheme.textPrimary)
                            .frame(maxWidth: .infinity)
                            .padding(12)
                            .background(BaoziTheme.surface)
                            .cornerRadius(8)
                            .padding(.horizontal, 16)
                    }
                    .disabled(apiKey.trimmingCharacters(in: .whitespaces).isEmpty || isWorking)
                }
            }
        }
    }

    private var allowsLocalEnvApiKey: Bool {
        server.isLocal
    }

    private var isChatGPTAccount: Bool {
        if case .chatgpt? = server.account {
            return true
        }
        return false
    }
    private var authColor: Color {
        switch server.account {
        case .chatgpt?:
            return BaoziTheme.accent
        case .apiKey?:
            return Color(hex: "#00AAFF")
        case nil:
            return BaoziTheme.textMuted
        }
    }

    private var authTitle: String {
        switch server.account {
        case .chatgpt(let email, _)?:
            return email.isEmpty ? "ChatGPT" : email
        case .apiKey?:
            return String(localized: "API Key")
        case nil:
            return String(localized: "Not logged in")
        }
    }

    private var authSubtitle: String? {
        switch server.account {
        case .chatgpt?:
            return String(localized: "ChatGPT account")
        case .apiKey?:
            return String(localized: "OpenAI API key")
        case nil:
            return nil
        }
    }

    private func refreshAccount() async {
        do {
            _ = try await appModel.client.refreshAccount(
                serverId: server.serverId,
                params: AppRefreshAccountRequest(refreshToken: false)
            )
            await appModel.refreshSnapshot()
            authError = nil
        } catch {
            authError = error.localizedDescription
        }
    }

    private func loginWithChatGPT() async {
        guard server.isLocal else {
            authError = "Account login is only available for the local server."
            return
        }
        do {
            authError = nil
            try await appModel.loginLocalChatGPTAccount(serverId: server.serverId)
        } catch ChatGPTOAuthError.cancelled {
            return
        } catch {
            authError = error.localizedDescription
        }
    }

    private func saveApiKey(_ key: String) async {
        guard server.isLocal else {
            authError = "API keys can only be saved for the local server."
            return
        }
        do {
            authError = nil
            try OpenAIApiKeyStore.shared.save(key)
            if case .apiKey? = server.account {
                _ = try await appModel.client.logoutAccount(serverId: server.serverId)
            }
            try await appModel.restartLocalServer()
            hasStoredApiKey = OpenAIApiKeyStore.shared.hasStoredKey
            guard hasStoredApiKey else {
                authError = "API key did not persist locally."
                return
            }
            dismiss()
        } catch {
            authError = error.localizedDescription
        }
    }

    private func logout() async {
        guard server.isLocal else {
            authError = "Account logout is only available for the local server."
            return
        }
        do {
            try? ChatGPTOAuthTokenStore.shared.clear()
            try? OpenAIApiKeyStore.shared.clear()
            _ = try await appModel.client.logoutAccount(serverId: server.serverId)
            try await appModel.restartLocalServer()
            authError = nil
        } catch {
            authError = error.localizedDescription
        }
    }
}

private struct AccountDisconnectedView: View {
    let dismiss: DismissAction

    var body: some View {
        NavigationStack {
            ZStack {
                BaoziTheme.backgroundGradient.ignoresSafeArea()
                VStack(spacing: 16) {
                    Text("Local Codex isn't running")
                        .baoziFont(.subheadline)
                        .foregroundColor(BaoziTheme.textPrimary)
                    Text("ChatGPT login and API key entry require the local Codex bridge.")
                        .baoziFont(.caption)
                        .foregroundColor(BaoziTheme.textSecondary)
                        .multilineTextAlignment(.center)
                        .padding(.horizontal, 24)
                }
                .frame(maxWidth: .infinity, maxHeight: .infinity)
            }
            .navigationTitle("Account")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarTrailing) {
                    Button("Done") { dismiss() }
                        .foregroundColor(BaoziTheme.accent)
                }
            }
        }
    }
}

#if DEBUG
#Preview("Account") {
    BaoziPreviewScene(includeBackground: false) {
        AccountView()
    }
}
#endif
