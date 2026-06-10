import SwiftUI

struct PetSettingsView: View {
    @Environment(AppModel.self) private var appModel
    @State private var controller = PetOverlayController.shared
    @State private var selectedServerId = ""
    @State private var pets: [AppPetSummary] = []
    @State private var isLoading = false
    @State private var errorMessage: String?

    private var connectedServers: [AppServerSnapshot] {
        appModel.snapshot?.servers.filter(\.isConnected) ?? []
    }

    var body: some View {
        Form {
            Section {
                Toggle(isOn: Binding(
                    get: { controller.visible },
                    set: { controller.setVisible($0) }
                )) {
                    VStack(alignment: .leading, spacing: 2) {
                        Text("Show Pet")
                            .baoziFont(.subheadline)
                            .foregroundColor(BaoziTheme.textPrimary)
                        Text(controller.selectedPet?.displayName ?? "No pet selected")
                            .baoziFont(.caption)
                            .foregroundColor(BaoziTheme.textSecondary)
                    }
                }
                .tint(BaoziTheme.accent)
                .listRowBackground(BaoziTheme.surface.opacity(0.6))
            } header: {
                Text("Wake")
                    .foregroundColor(BaoziTheme.textSecondary)
            }

            Section {
                if connectedServers.isEmpty {
                    Text("Connect to a server first")
                        .baoziFont(.footnote)
                        .foregroundColor(BaoziTheme.textMuted)
                } else {
                    ForEach(connectedServers, id: \.serverId) { server in
                        Button {
                            selectedServerId = server.serverId
                            Task { await refreshPets() }
                        } label: {
                            HStack {
                                VStack(alignment: .leading, spacing: 2) {
                                    Text(server.displayName)
                                        .baoziFont(.subheadline)
                                        .foregroundColor(BaoziTheme.textPrimary)
                                    Text(server.connectionModeLabel)
                                        .baoziFont(.caption)
                                        .foregroundColor(BaoziTheme.textSecondary)
                                }
                                Spacer()
                                if server.serverId == selectedServerId {
                                    Image(systemName: "checkmark")
                                        .foregroundColor(BaoziTheme.accentStrong)
                                }
                            }
                        }
                    }
                }
            } header: {
                Text("Server")
                    .foregroundColor(BaoziTheme.textSecondary)
            }

            Section {
                if selectedServerId.isEmpty {
                    Text("No server selected")
                        .foregroundColor(BaoziTheme.textMuted)
                } else if isLoading {
                    HStack {
                        ProgressView().tint(BaoziTheme.accent)
                        Text("Loading pets")
                            .foregroundColor(BaoziTheme.textSecondary)
                    }
                } else if let errorMessage {
                    Text(errorMessage)
                        .foregroundColor(BaoziTheme.danger)
                } else if pets.isEmpty {
                    Text("~/.codex/pets has no hatch-pet packages")
                        .foregroundColor(BaoziTheme.textMuted)
                } else {
                    ForEach(pets, id: \.id) { pet in
                        Button {
                            guard pet.hasValidSpritesheet else { return }
                            Task {
                                await controller.selectPet(
                                    appModel: appModel,
                                    serverId: selectedServerId,
                                    pet: pet
                                )
                            }
                        } label: {
                            HStack {
                                VStack(alignment: .leading, spacing: 2) {
                                    Text(pet.displayName)
                                        .baoziFont(.subheadline)
                                        .foregroundColor(pet.hasValidSpritesheet ? BaoziTheme.textPrimary : BaoziTheme.textMuted)
                                    Text(pet.validationError ?? pet.description ?? pet.sourcePath)
                                        .baoziFont(.caption)
                                        .foregroundColor(BaoziTheme.textSecondary)
                                        .lineLimit(2)
                                }
                                Spacer()
                                if controller.isLoading,
                                   controller.selectedPet?.id == pet.id,
                                   controller.selectedPet?.serverId == selectedServerId {
                                    ProgressView().tint(BaoziTheme.accent)
                                } else if controller.selectedPet?.id == pet.id,
                                          controller.selectedPet?.serverId == selectedServerId {
                                    Image(systemName: "checkmark")
                                        .foregroundColor(BaoziTheme.accentStrong)
                                }
                            }
                        }
                        .disabled(!pet.hasValidSpritesheet)
                    }
                }

                if let message = controller.errorMessage {
                    Text(message)
                        .foregroundColor(BaoziTheme.danger)
                }
            } header: {
                HStack {
                    Text("Pets")
                    Spacer()
                    Button("Refresh") {
                        Task { await refreshPets() }
                    }
                    .disabled(selectedServerId.isEmpty || isLoading)
                }
                .foregroundColor(BaoziTheme.textSecondary)
            }
        }
        .scrollContentBackground(.hidden)
        .background(BaoziTheme.backgroundGradient.ignoresSafeArea())
        .navigationTitle("Pet")
        .navigationBarTitleDisplayMode(.inline)
        .task {
            if selectedServerId.isEmpty {
                selectedServerId = controller.selectedPet?.serverId
                    ?? appModel.snapshot?.activeThread?.serverId
                    ?? connectedServers.first?.serverId
                    ?? ""
            }
            await refreshPets()
        }
    }

    @MainActor
    private func refreshPets() async {
        guard !selectedServerId.isEmpty else { return }
        isLoading = true
        errorMessage = nil
        do {
            pets = try await appModel.client.listPets(serverId: selectedServerId)
        } catch {
            pets = []
            errorMessage = error.localizedDescription
        }
        isLoading = false
    }
}
