import Foundation
import SwiftUI
import UIKit
import os
import Observation

struct DirectoryPickerServerOption: Identifiable, Hashable {
    let id: String
    let name: String
    let sourceLabel: String
}

private struct DirectoryPathBreadcrumb: Identifiable {
    let id: String
    let label: String
    let path: String
}

private enum DirectoryPickerStrings {
    static let title = String(localized: "directory_picker_title")
    static let changeServer = String(localized: "directory_picker_change_server")
    static let searchFolders = String(localized: "directory_picker_search_folders")
    static let upOneLevel = String(localized: "directory_picker_up_one_level")
    static let loadError = String(localized: "directory_picker_load_error")
    static let retry = String(localized: "directory_picker_retry")
    static let recentDirectories = String(localized: "directory_picker_recent_directories")
    static let clearRecentDirectories = String(localized: "directory_picker_clear_recent_directories")
    static let recentFooter = String(localized: "directory_picker_recent_footer")
    static let noSubdirectories = String(localized: "directory_picker_no_subdirectories")
    static let chooseFolderHelper = String(localized: "directory_picker_choose_folder_helper")
    static let selectFolder = String(localized: "directory_picker_select_folder")
    static let cancel = String(localized: "directory_picker_cancel")
    static let clearRecentTitle = String(localized: "directory_picker_clear_recent_title")
    static let clearRecentMessage = String(localized: "directory_picker_clear_recent_message")
    static let clear = String(localized: "directory_picker_clear")
    static let noServerSelected = String(localized: "directory_picker_no_server_selected")
    static let serverNotConnected = String(localized: "directory_picker_server_not_connected")
    static let newFolder = String(localized: "directory_picker_new_folder")
    static let newFolderTitle = String(localized: "directory_picker_new_folder_title")
    static let newFolderPlaceholder = String(localized: "directory_picker_new_folder_placeholder")
    static let create = String(localized: "directory_picker_create")
    static let createFolderFailed = String(localized: "directory_picker_create_folder_failed")
    static let goToPath = String(localized: "directory_picker_go_to_path")
    static let goToPathTitle = String(localized: "directory_picker_go_to_path_title")
    static let pathPlaceholder = String(localized: "directory_picker_path_placeholder")
    static let go = String(localized: "directory_picker_go")

    static func connectedServer(_ label: String) -> String {
        String.localizedStringWithFormat(String(localized: "directory_picker_connected_server"), label)
    }

    static func noMatches(_ query: String) -> String {
        String.localizedStringWithFormat(String(localized: "directory_picker_no_matches"), query)
    }

    static func continueIn(_ folder: String) -> String {
        String.localizedStringWithFormat(String(localized: "directory_picker_continue_in_folder"), folder)
    }

}

private let directoryPickerSignpostLog = OSLog(
    subsystem: Bundle.main.bundleIdentifier ?? "com.kris99.baozi.ios",
    category: "DirectoryPicker"
)

private func isDisconnectedClientError(_ error: Error) -> Bool {
    switch error {
    case let ClientError.Transport(message):
        return message.localizedCaseInsensitiveContains("disconnected")
    case let ClientError.Rpc(message):
        return message.localizedCaseInsensitiveContains("transport error") &&
            message.localizedCaseInsensitiveContains("disconnected")
    default:
        return false
    }
}

@MainActor
@Observable
private final class DirectoryPickerSheetModel {
    var currentPath = ""
    var allEntries: [String] = []
    var recentEntries: [RecentDirectoryEntry] = []
    var isLoading = true
    var errorMessage: String?
    var showHiddenDirectories = false
    var searchQuery = ""
    var homePath = ""

    @ObservationIgnored private var lastLoadedServerId = ""

    private static let relativeFormatter: RelativeDateTimeFormatter = {
        let formatter = RelativeDateTimeFormatter()
        formatter.unitsStyle = .abbreviated
        return formatter
    }()

    var trimmedSearchQuery: String {
        searchQuery.trimmingCharacters(in: .whitespacesAndNewlines)
    }

    var isLocal: Bool = false

    var canNavigateUp: Bool {
        guard !currentPath.isEmpty, !RemotePath.parse(path: currentPath).isRoot() else { return false }
        // Clamp the local picker at the user-facing `~` anchor. Everything
        // above it is iOS container internals the user has no business
        // poking at.
        if isLocal, currentPath == HomeAnchor.path { return false }
        return true
    }

    func visibleEntries() -> [String] {
        let hiddenFiltered = showHiddenDirectories ? allEntries : allEntries.filter { !$0.hasPrefix(".") }
        guard !trimmedSearchQuery.isEmpty else { return hiddenFiltered }
        return hiddenFiltered.filter { $0.localizedCaseInsensitiveContains(trimmedSearchQuery) }
    }

    func emptyMessage() -> String {
        if trimmedSearchQuery.isEmpty {
            return DirectoryPickerStrings.noSubdirectories
        }
        return DirectoryPickerStrings.noMatches(trimmedSearchQuery)
    }

    func pathSegments() -> [DirectoryPathBreadcrumb] {
        let raw = RemotePath.parse(path: currentPath).segments().map {
            DirectoryPathBreadcrumb(id: $0.fullPath, label: $0.label, path: $0.fullPath)
        }
        guard isLocal else { return raw }
        // Hide every breadcrumb above the user-facing `~` anchor and
        // relabel the anchor segment itself to "~" so the trail reads
        // `~ / projects / foo` instead of `var / mobile / … / codex / projects / foo`.
        let home = HomeAnchor.path
        let homeRoot = DirectoryPathBreadcrumb(id: home, label: "~", path: home)
        let suffix = raw.drop { $0.path != home }.dropFirst()
        return [homeRoot] + Array(suffix)
    }

    func relativeDate(for date: Date) -> String {
        Self.relativeFormatter.localizedString(for: date, relativeTo: Date())
    }

    func handleServerSelectionChanged(_ serverId: String) {
        if lastLoadedServerId != serverId {
            searchQuery = ""
            lastLoadedServerId = serverId
        }
        refreshRecentEntries(serverId: serverId)
    }

    func loadInitialPath(
        selectedServerId: String,
        appModel: AppModel,
        isLocalServer: Bool
    ) async {
        self.isLocal = isLocalServer
        let signpostID = OSSignpostID(log: directoryPickerSignpostLog)
        os_signpost(
            .begin,
            log: directoryPickerSignpostLog,
            name: "LoadInitialPath",
            signpostID: signpostID,
            "server=%{public}@",
            selectedServerId
        )
        defer {
            os_signpost(
                .end,
                log: directoryPickerSignpostLog,
                name: "LoadInitialPath",
                signpostID: signpostID
            )
        }

        let targetServerId = selectedServerId.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !targetServerId.isEmpty else {
            isLoading = false
            allEntries = []
            errorMessage = DirectoryPickerStrings.noServerSelected
            currentPath = ""
            homePath = ""
            return
        }

        isLoading = true
        errorMessage = nil
        allEntries = []
        currentPath = ""
        homePath = ""

        let home = await resolveHome(for: targetServerId, appModel: appModel, isLocalServer: isLocalServer)
        guard targetServerId == selectedServerId else { return }
        homePath = home
        currentPath = home
        await listDirectory(for: targetServerId, path: home, appModel: appModel, isLocalServer: isLocalServer)
    }

    func listDirectory(
        for serverId: String,
        path: String,
        appModel: AppModel,
        isLocalServer: Bool
    ) async {
        let signpostID = OSSignpostID(log: directoryPickerSignpostLog)
        os_signpost(
            .begin,
            log: directoryPickerSignpostLog,
            name: "ListDirectory",
            signpostID: signpostID,
            "server=%{public}@ path=%{public}@",
            serverId,
            path
        )
        defer {
            os_signpost(
                .end,
                log: directoryPickerSignpostLog,
                name: "ListDirectory",
                signpostID: signpostID
            )
        }

        guard appModel.snapshot?.servers.first(where: { $0.serverId == serverId })?.canBrowseDirectories == true else {
            if serverId == lastLoadedServerId {
                isLoading = false
                allEntries = []
                errorMessage = DirectoryPickerStrings.serverNotConnected
            }
            return
        }

        let normalizedPath = path.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty ? "/" : path
        isLoading = true
        errorMessage = nil

        if isLocalServer {
            await listLocalDirectory(normalizedPath, serverId: serverId)
        } else {
            await listRemoteDirectory(normalizedPath, serverId: serverId, appModel: appModel)
        }

        if serverId == lastLoadedServerId {
            isLoading = false
        }
    }

    private func listLocalDirectory(_ path: String, serverId: String) async {
        // Local paths live inside the iSH fakefs, which iOS-side `FileManager`
        // cannot see. Route directory enumeration through the iSH shell so we
        // get the actual fakefs contents. BusyBox-safe pipeline (no GNU
        // `-printf`).
        let result = await IshFS.run(
            "find \(IshFS.shellQuote(path)) -mindepth 1 -maxdepth 1 -type d 2>/dev/null | awk -F/ '{print $NF}' | sort"
        )
        guard serverId == lastLoadedServerId else { return }
        guard result.exitCode == 0 else {
            errorMessage = result.output.isEmpty
                ? "Couldn't list \(path)"
                : result.output.trimmingCharacters(in: .whitespacesAndNewlines)
            return
        }
        let dirs = result.output
            .split(separator: "\n", omittingEmptySubsequences: true)
            .map(String.init)
        allEntries = dirs
        withAnimation(.easeInOut(duration: 0.2)) {
            currentPath = path
        }
    }

    private func listRemoteDirectory(_ path: String, serverId: String, appModel: AppModel) async {
        do {
            let result = try await appModel.client.listRemoteDirectory(serverId: serverId, path: path)
            guard serverId == lastLoadedServerId else { return }
            allEntries = result.directories
            withAnimation(.easeInOut(duration: 0.2)) {
                currentPath = result.path
            }
        } catch {
            guard serverId == lastLoadedServerId else { return }
            errorMessage = isDisconnectedClientError(error) ?
                DirectoryPickerStrings.serverNotConnected :
                error.localizedDescription
        }
    }

    func navigateInto(
        _ name: String,
        selectedServerId: String,
        appModel: AppModel,
        isLocalServer: Bool
    ) async {
        let nextPath = RemotePath.parse(path: currentPath).join(name: name).asString()
        await listDirectory(for: selectedServerId, path: nextPath, appModel: appModel, isLocalServer: isLocalServer)
    }

    func navigateUp(
        selectedServerId: String,
        appModel: AppModel,
        isLocalServer: Bool
    ) async {
        let nextPath = RemotePath.parse(path: currentPath).parent().asString()
        await listDirectory(for: selectedServerId, path: nextPath, appModel: appModel, isLocalServer: isLocalServer)
    }

    func navigateToPath(
        _ path: String,
        selectedServerId: String,
        appModel: AppModel,
        isLocalServer: Bool
    ) async {
        await listDirectory(for: selectedServerId, path: path, appModel: appModel, isLocalServer: isLocalServer)
    }

    /// Create a new subdirectory under `currentPath` and navigate into it.
    /// Returns an error string on failure; nil on success.
    @discardableResult
    func createSubdirectory(
        name: String,
        selectedServerId: String,
        appModel: AppModel,
        isLocalServer: Bool
    ) async -> String? {
        let trimmed = name
            .trimmingCharacters(in: .whitespacesAndNewlines)
            .trimmingCharacters(in: CharacterSet(charactersIn: "/\\"))
        guard !trimmed.isEmpty else { return nil }
        guard !currentPath.isEmpty else {
            return DirectoryPickerStrings.createFolderFailed
        }
        let target = RemotePath.parse(path: currentPath).join(name: trimmed).asString()
        do {
            if isLocalServer {
                let result = await IshFS.run("mkdir -p \(IshFS.shellQuote(target))")
                if result.exitCode != 0 {
                    return result.output.isEmpty
                        ? DirectoryPickerStrings.createFolderFailed
                        : result.output.trimmingCharacters(in: .whitespacesAndNewlines)
                }
            } else {
                try await appModel.client.createRemoteDirectory(
                    serverId: selectedServerId,
                    path: target
                )
            }
        } catch {
            return error.localizedDescription
        }
        await listDirectory(
            for: selectedServerId,
            path: target,
            appModel: appModel,
            isLocalServer: isLocalServer
        )
        return nil
    }

    func removeRecentEntry(_ entry: RecentDirectoryEntry, selectedServerId: String) {
        withAnimation(.easeInOut(duration: 0.2)) {
            recentEntries = RecentDirectoryStore.shared.remove(path: entry.path, for: selectedServerId, limit: 3)
        }
    }

    func clearRecentEntries(selectedServerId: String) {
        withAnimation(.easeInOut(duration: 0.2)) {
            recentEntries = RecentDirectoryStore.shared.clear(for: selectedServerId)
        }
    }

    private func refreshRecentEntries(serverId: String) {
        recentEntries = RecentDirectoryStore.shared.recentDirectories(for: serverId, limit: 3)
    }

    private func resolveHome(
        for serverId: String,
        appModel: AppModel,
        isLocalServer: Bool
    ) async -> String {
        guard appModel.snapshot?.servers.first(where: { $0.serverId == serverId })?.canBrowseDirectories == true else {
            return "/"
        }
        if isLocalServer {
            return HomeAnchor.path
        }
        do {
            return try await appModel.client.resolveRemoteHome(serverId: serverId)
        } catch {
            if isDisconnectedClientError(error) {
                errorMessage = DirectoryPickerStrings.serverNotConnected
            }
            return "/"
        }
    }

}

struct DirectoryPickerView: View {
    let servers: [DirectoryPickerServerOption]
    @Binding var selectedServerId: String
    var onServerChanged: ((String) -> Void)?
    var onDirectorySelected: ((String, String) -> Void)?
    var onDismissRequested: (() -> Void)?

    @Environment(AppModel.self) private var appModel
    @State private var model = DirectoryPickerSheetModel()
    @State private var showClearRecentsConfirmation = false
    @State private var showNewFolderAlert = false
    @State private var showGoToPathAlert = false
    @State private var newFolderName = ""
    @State private var pathInput = ""
    @State private var newFolderError: String?

    private var selectedServerOption: DirectoryPickerServerOption? {
        servers.first { $0.id == selectedServerId }
    }

    private var selectedServerSnapshot: AppServerSnapshot? {
        appModel.snapshot?.servers.first(where: { $0.serverId == selectedServerId })
    }

    private var selectedServerIsLocal: Bool {
        selectedServerSnapshot?.isLocal ?? false
    }

    private var canSelectPath: Bool {
        !model.currentPath.isEmpty &&
            selectedServerSnapshot?.canBrowseDirectories == true &&
            selectedServerOption != nil
    }

    private var showRecentDirectories: Bool {
        model.trimmedSearchQuery.isEmpty && !model.recentEntries.isEmpty
    }

    private var mostRecentEntry: RecentDirectoryEntry? {
        model.recentEntries.first
    }

    private var searchQueryBinding: Binding<String> {
        Binding(
            get: { model.searchQuery },
            set: { model.searchQuery = $0 }
        )
    }

    var body: some View {
        ZStack {
            BaoziTheme.backgroundGradient.ignoresSafeArea()
            VStack(spacing: 0) {
                controls
                Divider().background(BaoziTheme.separator)
                content
            }
        }
        .safeAreaInset(edge: .bottom) {
            bottomActionBar
        }
        .navigationTitle(DirectoryPickerStrings.title)
        .navigationBarTitleDisplayMode(.inline)
        .interactiveDismissDisabled(model.canNavigateUp)
        .task(id: selectedServerId) {
            onServerChanged?(selectedServerId)
            model.handleServerSelectionChanged(selectedServerId)
            await model.loadInitialPath(
                selectedServerId: selectedServerId,
                appModel: appModel,
                isLocalServer: selectedServerIsLocal
            )
        }
        .onChange(of: servers.map(\.id)) { _, ids in
            if !ids.contains(selectedServerId), let fallback = ids.first {
                selectedServerId = fallback
            }
        }
        .confirmationDialog(
            DirectoryPickerStrings.clearRecentTitle,
            isPresented: $showClearRecentsConfirmation,
            titleVisibility: .visible
        ) {
            Button(DirectoryPickerStrings.clear, role: .destructive) {
                model.clearRecentEntries(selectedServerId: selectedServerId)
            }
            Button(DirectoryPickerStrings.cancel, role: .cancel) {}
        } message: {
            Text(DirectoryPickerStrings.clearRecentMessage)
        }
        .alert(DirectoryPickerStrings.newFolderTitle, isPresented: $showNewFolderAlert) {
            TextField(DirectoryPickerStrings.newFolderPlaceholder, text: $newFolderName)
                .textInputAutocapitalization(.never)
                .autocorrectionDisabled(true)
            Button(DirectoryPickerStrings.cancel, role: .cancel) { newFolderName = "" }
            Button(DirectoryPickerStrings.create) {
                let name = newFolderName
                newFolderName = ""
                Task {
                    if let err = await model.createSubdirectory(
                        name: name,
                        selectedServerId: selectedServerId,
                        appModel: appModel,
                        isLocalServer: selectedServerIsLocal
                    ) {
                        newFolderError = err
                    } else {
                        emitSuccessHaptic()
                    }
                }
            }
        } message: {
            Text(PathDisplay.display(model.currentPath, isLocal: selectedServerIsLocal))
        }
        .alert(DirectoryPickerStrings.goToPathTitle, isPresented: $showGoToPathAlert) {
            TextField(DirectoryPickerStrings.pathPlaceholder, text: $pathInput)
                .textInputAutocapitalization(.never)
                .autocorrectionDisabled(true)
            Button(DirectoryPickerStrings.cancel, role: .cancel) { pathInput = "" }
            Button(DirectoryPickerStrings.go) {
                let target = PathDisplay.expand(
                    pathInput,
                    isLocal: selectedServerIsLocal,
                    remoteHome: model.homePath
                )
                    .trimmingCharacters(in: .whitespacesAndNewlines)
                pathInput = ""
                guard !target.isEmpty else { return }
                Task {
                    await model.navigateToPath(
                        target,
                        selectedServerId: selectedServerId,
                        appModel: appModel,
                        isLocalServer: selectedServerIsLocal
                    )
                }
            }
        }
        .alert(DirectoryPickerStrings.createFolderFailed, isPresented: Binding(
            get: { newFolderError != nil },
            set: { if !$0 { newFolderError = nil } }
        )) {
            Button("OK", role: .cancel) { newFolderError = nil }
        } message: {
            Text(newFolderError ?? "")
        }
    }

    private var controls: some View {
        VStack(spacing: 8) {
            HStack(spacing: 8) {
                Text(
                    DirectoryPickerStrings.connectedServer(
                        selectedServerOption.map { "\($0.name) • \($0.sourceLabel)" } ??
                            DirectoryPickerStrings.noServerSelected
                    )
                )
                .baoziFont(.caption)
                .foregroundColor(selectedServerOption == nil ? BaoziTheme.textMuted : BaoziTheme.textSecondary)
                .lineLimit(1)

                Spacer()

                if !servers.isEmpty {
                    Menu(DirectoryPickerStrings.changeServer) {
                        ForEach(servers) { server in
                            Button("\(server.name) • \(server.sourceLabel)") {
                                selectedServerId = server.id
                            }
                        }
                    }
                    .baoziFont(.caption)
                    .foregroundColor(BaoziTheme.accent)
                }

                Button {
                    model.showHiddenDirectories.toggle()
                } label: {
                    Image(systemName: model.showHiddenDirectories ? "eye" : "eye.slash")
                        .foregroundColor(model.showHiddenDirectories ? BaoziTheme.accent : BaoziTheme.textSecondary)
                }
                .accessibilityLabel(
                    model.showHiddenDirectories ?
                        String(localized: "directory_picker_hide_hidden_folders") :
                        String(localized: "directory_picker_show_hidden_folders")
                )
            }

            HStack(spacing: 8) {
                Image(systemName: "magnifyingglass")
                    .foregroundColor(BaoziTheme.textMuted)
                TextField(
                    DirectoryPickerStrings.searchFolders,
                    text: searchQueryBinding
                )
                .baoziFont(.caption)
                .foregroundColor(BaoziTheme.textPrimary)
                .textInputAutocapitalization(.never)
                .autocorrectionDisabled(true)

                if !model.searchQuery.isEmpty {
                    Button {
                        model.searchQuery = ""
                    } label: {
                        Image(systemName: "xmark.circle.fill")
                            .foregroundColor(BaoziTheme.textMuted)
                    }
                    .buttonStyle(.plain)
                }
            }
            .padding(.horizontal, 10)
            .padding(.vertical, 8)
            .background(BaoziTheme.surface.opacity(0.65))
            .overlay(
                RoundedRectangle(cornerRadius: 8)
                    .stroke(BaoziTheme.border.opacity(0.85), lineWidth: 1)
            )
            .cornerRadius(8)

            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 8) {
                    Button {
                        Task {
                            await model.navigateUp(
                                selectedServerId: selectedServerId,
                                appModel: appModel,
                                isLocalServer: selectedServerIsLocal
                            )
                        }
                    } label: {
                        Label(DirectoryPickerStrings.upOneLevel, systemImage: "arrow.up.backward")
                            .baoziFont(.caption)
                    }
                    .disabled(!model.canNavigateUp)

                    Button {
                        if selectedServerIsLocal {
                            pathInput = PathDisplay.display(model.currentPath, isLocal: true)
                        } else {
                            pathInput = model.currentPath
                        }
                        showGoToPathAlert = true
                    } label: {
                        Label(DirectoryPickerStrings.goToPath, systemImage: "arrow.right.to.line")
                            .baoziFont(.caption)
                    }
                    .disabled(selectedServerSnapshot?.canBrowseDirectories != true)

                    Button {
                        newFolderName = ""
                        showNewFolderAlert = true
                    } label: {
                        Label(DirectoryPickerStrings.newFolder, systemImage: "folder.badge.plus")
                            .baoziFont(.caption)
                    }
                    .disabled(!canSelectPath)

                    ForEach(model.pathSegments()) { segment in
                        Button {
                            Task {
                                await model.navigateToPath(
                                    segment.path,
                                    selectedServerId: selectedServerId,
                                    appModel: appModel,
                                    isLocalServer: selectedServerIsLocal
                                )
                            }
                        } label: {
                            Text(segment.label)
                                .baoziFont(.caption)
                                .foregroundColor(segment.path == model.currentPath ? BaoziTheme.textOnAccent : BaoziTheme.textSecondary)
                                .padding(.horizontal, 10)
                                .padding(.vertical, 6)
                                .background(
                                    RoundedRectangle(cornerRadius: 8)
                                        .fill(segment.path == model.currentPath ? BaoziTheme.accent : BaoziTheme.surface.opacity(0.65))
                                )
                        }
                        .buttonStyle(.plain)
                    }
                }
            }
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 10)
        .background(.ultraThinMaterial)
    }

    @ViewBuilder
    private var content: some View {
        if model.isLoading {
            ProgressView().tint(BaoziTheme.accent).frame(maxHeight: .infinity)
        } else if let err = model.errorMessage {
            VStack(spacing: 12) {
                Text(DirectoryPickerStrings.loadError)
                    .baoziFont(.caption)
                    .foregroundColor(BaoziTheme.danger)
                Text(err)
                    .baoziFont(.caption2)
                    .foregroundColor(BaoziTheme.textSecondary)
                    .multilineTextAlignment(.center)
                    .padding(.horizontal, 32)
                HStack(spacing: 12) {
                    Button(DirectoryPickerStrings.retry) {
                        Task {
                            await model.listDirectory(
                                for: selectedServerId,
                                path: model.currentPath,
                                appModel: appModel,
                                isLocalServer: selectedServerIsLocal
                            )
                        }
                    }
                    .foregroundColor(BaoziTheme.accent)

                    Button(DirectoryPickerStrings.changeServer) {
                        selectNextServer()
                    }
                    .foregroundColor(BaoziTheme.accent)
                }
            }
            .frame(maxHeight: .infinity)
        } else {
            directoryList
        }
    }

    private var directoryList: some View {
        List {
            if let recent = mostRecentEntry {
                Section {
                    Button {
                        emitSuccessHaptic()
                        withAnimation(.easeInOut(duration: 0.16)) {
                            onDirectorySelected?(selectedServerId, recent.path)
                        }
                    } label: {
                        HStack(spacing: 10) {
                            Image(systemName: "play.fill")
                                .foregroundColor(BaoziTheme.accent)
                                .frame(width: 20)
                            VStack(alignment: .leading, spacing: 2) {
                                Text(DirectoryPickerStrings.continueIn((recent.path as NSString).lastPathComponent))
                                    .baoziFont(.subheadline)
                                    .foregroundColor(BaoziTheme.textPrimary)
                                    .lineLimit(1)
                                Text(PathDisplay.display(recent.path, isLocal: selectedServerIsLocal))
                                    .baoziFont(.caption2)
                                    .foregroundColor(BaoziTheme.textMuted)
                                    .lineLimit(1)
                            }
                            Spacer()
                        }
                    }
                }
                .listRowBackground(BaoziTheme.surface.opacity(0.6))
            }

            if showRecentDirectories {
                Section {
                    ForEach(model.recentEntries) { recent in
                        Button {
                            emitSuccessHaptic()
                            withAnimation(.easeInOut(duration: 0.16)) {
                                onDirectorySelected?(selectedServerId, recent.path)
                            }
                        } label: {
                            HStack(spacing: 10) {
                                Image(systemName: "clock.arrow.circlepath")
                                    .foregroundColor(BaoziTheme.textSecondary)
                                    .frame(width: 20)
                                VStack(alignment: .leading, spacing: 2) {
                                    Text((recent.path as NSString).lastPathComponent)
                                        .baoziFont(.subheadline)
                                        .foregroundColor(BaoziTheme.textPrimary)
                                        .lineLimit(1)
                                    Text(PathDisplay.display(recent.path, isLocal: selectedServerIsLocal))
                                        .baoziFont(.caption2)
                                        .foregroundColor(BaoziTheme.textMuted)
                                        .lineLimit(1)
                                }
                                Spacer()
                                Text(model.relativeDate(for: recent.lastUsedAt))
                                    .baoziFont(.caption2)
                                    .foregroundColor(BaoziTheme.textSecondary)
                                    .lineLimit(1)
                            }
                        }
                        .swipeActions(edge: .trailing, allowsFullSwipe: true) {
                            Button(role: .destructive) {
                                model.removeRecentEntry(recent, selectedServerId: selectedServerId)
                            } label: {
                                Label(String(localized: "directory_picker_remove_recent"), systemImage: "trash")
                            }
                        }
                        .listRowBackground(BaoziTheme.surface.opacity(0.6))
                    }
                } header: {
                    HStack {
                        Text(DirectoryPickerStrings.recentDirectories)
                            .baoziFont(.caption)
                            .foregroundColor(BaoziTheme.textSecondary)
                        Spacer()
                        Menu {
                            Button(DirectoryPickerStrings.clearRecentDirectories, role: .destructive) {
                                showClearRecentsConfirmation = true
                            }
                        } label: {
                            Image(systemName: "ellipsis.circle")
                                .foregroundColor(BaoziTheme.textMuted)
                        }
                    }
                } footer: {
                    Text(DirectoryPickerStrings.recentFooter)
                        .baoziFont(.caption2)
                        .foregroundColor(BaoziTheme.textMuted)
                }
            }

            let visibleEntries = model.visibleEntries()
            if visibleEntries.isEmpty {
                Text(model.emptyMessage())
                    .baoziFont(.caption)
                    .foregroundColor(BaoziTheme.textMuted)
                    .listRowBackground(BaoziTheme.surface.opacity(0.6))
            } else {
                ForEach(visibleEntries, id: \.self) { entry in
                    Button {
                        emitSelectionHaptic()
                        Task {
                            await model.navigateInto(
                                entry,
                                selectedServerId: selectedServerId,
                                appModel: appModel,
                                isLocalServer: selectedServerIsLocal
                            )
                        }
                    } label: {
                        HStack(spacing: 10) {
                            Image(systemName: "folder.fill")
                                .foregroundColor(BaoziTheme.accent)
                                .frame(width: 20)
                            Text(entry)
                                .baoziFont(.subheadline)
                                .foregroundColor(BaoziTheme.textPrimary)
                            Spacer()
                            Image(systemName: "chevron.right")
                                .foregroundColor(BaoziTheme.textMuted)
                                .baoziFont(.caption)
                        }
                    }
                    .listRowBackground(BaoziTheme.surface.opacity(0.6))
                }
            }
        }
        .scrollContentBackground(.hidden)
        .animation(.easeInOut(duration: 0.2), value: model.recentEntries)
        .accessibilityIdentifier("directoryPicker.list")
    }

    private var bottomActionBar: some View {
        VStack(alignment: .leading, spacing: 8) {
            if !model.currentPath.isEmpty {
                Text(PathDisplay.display(model.currentPath, isLocal: selectedServerIsLocal))
                    .baoziFont(.caption)
                    .foregroundColor(BaoziTheme.textMuted)
                    .lineLimit(1)
                    .truncationMode(.middle)
                    .frame(maxWidth: .infinity, alignment: .leading)
            } else if !canSelectPath {
                Text(DirectoryPickerStrings.chooseFolderHelper)
                    .baoziFont(.caption)
                    .foregroundColor(BaoziTheme.textSecondary)
                    .frame(maxWidth: .infinity, alignment: .leading)
            }
            HStack(spacing: 10) {
                Button(DirectoryPickerStrings.cancel) {
                    onDismissRequested?()
                }
                .buttonStyle(.plain)
                .baoziFont(.subheadline)
                .foregroundColor(BaoziTheme.textSecondary)
                .frame(maxWidth: .infinity)
                .padding(.vertical, 10)
                .background(BaoziTheme.surface.opacity(0.65))
                .overlay(
                    RoundedRectangle(cornerRadius: 8)
                        .stroke(BaoziTheme.border.opacity(0.75), lineWidth: 1)
                )
                .cornerRadius(8)

                Button(DirectoryPickerStrings.selectFolder) {
                    emitSuccessHaptic()
                    withAnimation(.easeInOut(duration: 0.16)) {
                        onDirectorySelected?(selectedServerId, model.currentPath)
                    }
                }
                .accessibilityIdentifier("directoryPicker.selectFolderButton")
                .disabled(!canSelectPath)
                .buttonStyle(.plain)
                .baoziFont(.subheadline)
                .foregroundColor(canSelectPath ? BaoziTheme.textOnAccent : BaoziTheme.textMuted)
                .frame(maxWidth: .infinity)
                .padding(.vertical, 10)
                .background(canSelectPath ? BaoziTheme.accent : BaoziTheme.surface.opacity(0.65))
                .overlay(
                    RoundedRectangle(cornerRadius: 8)
                        .stroke(canSelectPath ? BaoziTheme.accent.opacity(0.8) : BaoziTheme.border.opacity(0.75), lineWidth: 1)
                )
                .cornerRadius(8)
            }
        }
        .padding(.horizontal, 16)
        .padding(.top, 8)
        .padding(.bottom, 8)
        .background(.ultraThinMaterial)
    }

    private func selectNextServer() {
        guard !servers.isEmpty else { return }
        guard let currentIndex = servers.firstIndex(where: { $0.id == selectedServerId }) else {
            selectedServerId = servers[0].id
            return
        }
        let nextIndex = (currentIndex + 1) % servers.count
        selectedServerId = servers[nextIndex].id
    }

    private func emitSelectionHaptic() {
        UIImpactFeedbackGenerator(style: .light).impactOccurred()
    }

    private func emitSuccessHaptic() {
        UINotificationFeedbackGenerator().notificationOccurred(.success)
    }
}

#if DEBUG
#Preview("Directory Picker") {
    NavigationStack {
        DirectoryPickerView(
            servers: [],
            selectedServerId: .constant(""),
            onDismissRequested: {}
        )
        .environment(BaoziPreviewData.makeDiscoveryAppModel())
    }
}
#endif
