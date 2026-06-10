import SwiftUI

struct SSHLoginSheet: View {
    let server: DiscoveredServer
    let onConnect: (ConnectionTarget) -> Void
    private let autoLoadSavedCredentials: Bool

    @Environment(\.dismiss) private var dismiss
    @State private var username: String
    @State private var password = ""
    @State private var isPasswordVisible = false
    @State private var useKey = false
    @State private var privateKey = ""
    @State private var passphrase = ""
    @State private var rememberCredentials = true
    @State private var unlockMacosKeychain = false
    @State private var hasSavedCredentials = false
    @State private var loadedSavedCredentials = false
    @State private var isConnecting = false
    @State private var errorMessage: String?

    init(
        server: DiscoveredServer,
        autoLoadSavedCredentials: Bool = true,
        initialUsername: String = "",
        onConnect: @escaping (ConnectionTarget) -> Void
    ) {
        self.server = server
        self.onConnect = onConnect
        self.autoLoadSavedCredentials = autoLoadSavedCredentials
        _username = State(initialValue: initialUsername)
    }

    private var sshPort: Int {
        Int(server.resolvedSSHPort)
    }

    private var hostDisplay: String {
        if sshPort == 22 {
            return server.hostname
        }
        return "\(server.hostname):\(sshPort)"
    }

    var body: some View {
        NavigationStack {
            ZStack {
                BaoziTheme.backgroundGradient.ignoresSafeArea()
                Form {
                    Section {
                        HStack(spacing: 12) {
                            Image(systemName: "terminal")
                                .foregroundColor(BaoziTheme.accent)
                            VStack(alignment: .leading, spacing: 2) {
                                Text(server.name)
                                    .baoziFont(.subheadline)
                                    .foregroundColor(BaoziTheme.textPrimary)
                                Text(hostDisplay)
                                    .baoziFont(.caption)
                                    .foregroundColor(BaoziTheme.textSecondary)
                            }
                        }
                    }
                    .listRowBackground(BaoziTheme.surface.opacity(0.6))

                    Section {
                        TextField("username", text: $username)
                            .baoziFont(.footnote)
                            .foregroundColor(BaoziTheme.textPrimary)
                            .textInputAutocapitalization(.never)
                            .autocorrectionDisabled(true)
                    } header: {
                        Text("Username")
                            .foregroundColor(BaoziTheme.textSecondary)
                    }
                    .listRowBackground(BaoziTheme.surface.opacity(0.6))

                    Section {
                        Picker("Method", selection: $useKey) {
                            Text("Password").tag(false)
                            Text("SSH Key").tag(true)
                        }
                        .pickerStyle(.segmented)
                        .listRowBackground(BaoziTheme.surface.opacity(0.6))

                        if useKey {
                            TextEditor(text: $privateKey)
                                .baoziFont(.caption)
                                .foregroundColor(BaoziTheme.textPrimary)
                                .scrollContentBackground(.hidden)
                                .frame(minHeight: 100)
                                .overlay(alignment: .topLeading) {
                                    if privateKey.isEmpty {
                                        Text("Paste private key here...")
                                            .baoziFont(.caption)
                                            .foregroundColor(BaoziTheme.textMuted)
                                            .padding(.top, 8)
                                            .padding(.leading, 4)
                                            .allowsHitTesting(false)
                                    }
                                }
                            SecureField("passphrase (optional)", text: $passphrase)
                                .baoziFont(.footnote)
                                .foregroundColor(BaoziTheme.textPrimary)
                        } else {
                            passwordInput

                            VStack(alignment: .leading, spacing: 4) {
                                Toggle(isOn: $unlockMacosKeychain) {
                                    Text("Unlock keychain (macOS)")
                                        .baoziFont(.footnote)
                                        .foregroundColor(BaoziTheme.textPrimary)
                                }
                                .tint(BaoziTheme.accent)

                                Text("Uses your SSH/login password during headless bootstrap. Required for tools like gh CLI auth.")
                                    .baoziFont(.caption)
                                    .foregroundColor(BaoziTheme.textSecondary)
                            }
                        }
                    } header: {
                        Text("Authentication")
                            .foregroundColor(BaoziTheme.textSecondary)
                    }
                    .listRowBackground(BaoziTheme.surface.opacity(0.6))

                    Section {
                        Toggle(isOn: $rememberCredentials) {
                            Text("Remember credentials on this device")
                                .baoziFont(.footnote)
                                .foregroundColor(BaoziTheme.textPrimary)
                        }
                        .tint(BaoziTheme.accent)

                        if hasSavedCredentials {
                            Button(role: .destructive) {
                                forgetSavedCredentials()
                            } label: {
                                Text("Forget saved credentials")
                                    .baoziFont(.footnote)
                            }
                        }
                    } header: {
                        Text("Saved Credentials")
                            .foregroundColor(BaoziTheme.textSecondary)
                    }
                    .listRowBackground(BaoziTheme.surface.opacity(0.6))

                    Section {
                        Button {
                            connect()
                        } label: {
                            HStack {
                                if isConnecting {
                                    ProgressView().tint(BaoziTheme.accent)
                                }
                                Text("Connect")
                                    .foregroundColor(BaoziTheme.accent)
                                    .baoziFont(.subheadline)
                            }
                        }
                        .disabled(isConnecting || username.isEmpty || (!useKey && password.isEmpty) || (useKey && privateKey.isEmpty))
                    }
                    .listRowBackground(BaoziTheme.surface.opacity(0.6))

                    if let err = errorMessage {
                        Section {
                            Text(err)
                                .foregroundColor(.red)
                                .baoziFont(.caption)
                        }
                        .listRowBackground(BaoziTheme.surface.opacity(0.6))
                    }
                }
                .scrollContentBackground(.hidden)
            }
            .navigationTitle("SSH Login")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarLeading) {
                    Button("Cancel") { dismiss() }
                        .foregroundColor(BaoziTheme.accent)
                }
            }
        }
        .task {
            guard autoLoadSavedCredentials else { return }
            loadSavedCredentialsIfNeeded()
        }
        .onChange(of: useKey) { _, isUsingKey in
            if isUsingKey {
                isPasswordVisible = false
                unlockMacosKeychain = false
            }
        }
    }

    private var passwordInput: some View {
        HStack(spacing: 8) {
            Group {
                if isPasswordVisible {
                    TextField("password", text: $password)
                        .textContentType(.password)
                } else {
                    SecureField("password", text: $password)
                        .textContentType(.password)
                }
            }
            .baoziFont(.footnote)
            .foregroundColor(BaoziTheme.textPrimary)
            .textInputAutocapitalization(.never)
            .autocorrectionDisabled(true)

            Button {
                isPasswordVisible.toggle()
            } label: {
                Image(systemName: isPasswordVisible ? "eye.slash" : "eye")
                    .foregroundColor(BaoziTheme.textSecondary)
            }
            .buttonStyle(.plain)
            .accessibilityLabel(isPasswordVisible ? "Hide password" : "Show password")
        }
    }

    private func connect() {
        let credentials: SSHCredentials
        if useKey {
            credentials = .key(
                username: username,
                privateKey: privateKey,
                passphrase: passphrase.isEmpty ? nil : passphrase
            )
        } else {
            credentials = .password(
                username: username,
                password: password,
                unlockMacosKeychain: unlockMacosKeychain
            )
        }
        isConnecting = true
        errorMessage = nil

        Task {
            do {
                do {
                    if rememberCredentials {
                        try SSHCredentialStore.shared.save(
                            savedCredential(from: credentials),
                            host: server.hostname,
                            port: sshPort
                        )
                        hasSavedCredentials = true
                    } else {
                        try SSHCredentialStore.shared.delete(host: server.hostname, port: sshPort)
                        hasSavedCredentials = false
                    }
                } catch {
                    NSLog("[SSH_CREDENTIALS] keychain update failed: %@", error.localizedDescription)
                }

                clearSensitiveInput()
                isConnecting = false
                onConnect(.sshThenRemote(host: server.hostname, credentials: credentials))
            } catch {
                isConnecting = false
                errorMessage = error.localizedDescription
            }
        }
    }

    private func loadSavedCredentialsIfNeeded() {
        guard !loadedSavedCredentials else { return }
        loadedSavedCredentials = true

        do {
            guard let saved = try SSHCredentialStore.shared.load(host: server.hostname, port: sshPort) else {
                hasSavedCredentials = false
                return
            }
            hasSavedCredentials = true
            rememberCredentials = true
            username = saved.username
            useKey = saved.method == .key
            if saved.method == .key {
                privateKey = saved.privateKey ?? ""
                passphrase = saved.passphrase ?? ""
                password = ""
                unlockMacosKeychain = false
            } else {
                password = saved.password ?? ""
                privateKey = ""
                passphrase = ""
                unlockMacosKeychain = saved.unlockMacosKeychain ?? false
            }
        } catch {
            NSLog("[SSH_CREDENTIALS] failed to load: %@", error.localizedDescription)
        }
    }

    private func forgetSavedCredentials() {
        do {
            try SSHCredentialStore.shared.delete(host: server.hostname, port: sshPort)
            hasSavedCredentials = false
            rememberCredentials = false
            clearSensitiveInput()
        } catch {
            NSLog("[SSH_CREDENTIALS] failed to delete: %@", error.localizedDescription)
        }
    }

    private func savedCredential(from credentials: SSHCredentials) -> SavedSSHCredential {
        switch credentials {
        case .password(let username, let password, let unlockMacosKeychain):
            return SavedSSHCredential(
                username: username,
                method: .password,
                password: password,
                privateKey: nil,
                passphrase: nil,
                unlockMacosKeychain: unlockMacosKeychain
            )
        case .key(let username, let privateKey, let passphrase):
            return SavedSSHCredential(
                username: username,
                method: .key,
                password: nil,
                privateKey: privateKey,
                passphrase: passphrase,
                unlockMacosKeychain: false
            )
        }
    }

    private func clearSensitiveInput() {
        password = ""
        isPasswordVisible = false
        privateKey = ""
        passphrase = ""
    }
}

#if DEBUG
#Preview("SSH Login") {
    SSHLoginSheet(
        server: BaoziPreviewData.sampleSSHServer,
        autoLoadSavedCredentials: false,
        initialUsername: "builder"
    ) { _ in }
}
#endif
