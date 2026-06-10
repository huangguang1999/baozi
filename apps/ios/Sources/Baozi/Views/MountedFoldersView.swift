import SwiftUI
import UniformTypeIdentifiers

struct MountedFoldersView: View {
    enum PickerMode: Equatable {
        case add
        case reconnect(UUID)
    }

    @Environment(\.dismiss) private var dismiss
    @State private var store = UserMountStore.shared
    @State private var pickerMode: PickerMode?
    @State private var pendingRemoval: UserMount?

    var body: some View {
        NavigationStack {
            ZStack {
                BaoziTheme.backgroundGradient.ignoresSafeArea()
                if store.mounts.isEmpty {
                    emptyState
                } else {
                    list
                }
            }
            .navigationTitle("Mounted folders")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarLeading) {
                    Button("Done") { dismiss() }
                        .foregroundColor(BaoziTheme.accent)
                }
                ToolbarItem(placement: .topBarTrailing) {
                    Button {
                        pickerMode = .add
                    } label: {
                        Image(systemName: "plus")
                    }
                    .foregroundColor(BaoziTheme.accent)
                }
            }
        }
        .fileImporter(
            isPresented: Binding(
                get: { pickerMode != nil },
                set: { if !$0 { pickerMode = nil } }
            ),
            allowedContentTypes: [.folder],
            allowsMultipleSelection: false
        ) { result in
            let mode = pickerMode
            pickerMode = nil
            handlePick(result: result, mode: mode)
        }
        .confirmationDialog(
            removalPrompt,
            isPresented: Binding(
                get: { pendingRemoval != nil },
                set: { if !$0 { pendingRemoval = nil } }
            ),
            titleVisibility: .visible,
            presenting: pendingRemoval
        ) { mount in
            Button("Remove", role: .destructive) {
                Task { await store.remove(id: mount.id) }
            }
            Button("Cancel", role: .cancel) {}
        }
    }

    // MARK: - List

    private var list: some View {
        ScrollView {
            VStack(spacing: 10) {
                ForEach(store.mounts) { mount in
                    row(for: mount)
                }
                footerExplainer
            }
            .padding(.horizontal, 16)
            .padding(.top, 12)
            .padding(.bottom, 24)
        }
    }

    private func row(for mount: UserMount) -> some View {
        let status = store.statuses[mount.id]
        return VStack(alignment: .leading, spacing: 8) {
            HStack(spacing: 10) {
                statusIcon(for: status)
                VStack(alignment: .leading, spacing: 2) {
                    Text(mount.name)
                        .baoziFont(.subheadline)
                        .foregroundColor(BaoziTheme.textPrimary)
                    Text("/mnt/\(mount.name)")
                        .baoziMonoFont(size: 11)
                        .foregroundColor(BaoziTheme.textMuted)
                }
                Spacer()
                Menu {
                    if needsReconnect(status) {
                        Button {
                            pickerMode = .reconnect(mount.id)
                        } label: {
                            Label("Reconnect", systemImage: "arrow.clockwise")
                        }
                    }
                    Button(role: .destructive) {
                        pendingRemoval = mount
                    } label: {
                        Label("Remove", systemImage: "trash")
                    }
                } label: {
                    Image(systemName: "ellipsis.circle")
                        .foregroundColor(BaoziTheme.textSecondary)
                }
            }
            Text(mount.displayPath)
                .baoziFont(.caption)
                .foregroundColor(BaoziTheme.textSecondary)
                .lineLimit(2)
                .truncationMode(.middle)
            if let detail = statusDetail(for: status) {
                Text(detail)
                    .baoziFont(.caption)
                    .foregroundColor(BaoziTheme.danger)
            }
        }
        .padding(14)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(
            RoundedRectangle(cornerRadius: 12, style: .continuous)
                .fill(BaoziTheme.surface.opacity(0.6))
        )
        .overlay(
            RoundedRectangle(cornerRadius: 12, style: .continuous)
                .stroke(BaoziTheme.textMuted.opacity(0.18), lineWidth: 0.6)
        )
    }

    private var footerExplainer: some View {
        Text("Mounts persist across launches. Removing only detaches the mount inside iSH; files in the source folder are not deleted.")
            .baoziFont(.caption)
            .foregroundColor(BaoziTheme.textMuted)
            .padding(.horizontal, 4)
            .padding(.top, 8)
    }

    // MARK: - Empty state

    private var emptyState: some View {
        VStack(spacing: 16) {
            Image(systemName: "externaldrive.badge.icloud")
                .font(.system(size: 44, weight: .light))
                .foregroundColor(BaoziTheme.accent)
            Text("No folders mounted")
                .baoziFont(.headline)
                .foregroundColor(BaoziTheme.textPrimary)
            Text("Pick a folder from Files (iCloud Drive, On My iPhone, or a third-party provider) to make it available inside iSH at /mnt/<name>.")
                .baoziFont(.caption)
                .foregroundColor(BaoziTheme.textSecondary)
                .multilineTextAlignment(.center)
                .padding(.horizontal, 32)
            Button {
                pickerMode = .add
            } label: {
                Text("Add folder")
                    .baoziFont(.subheadline)
                    .padding(.horizontal, 18)
                    .padding(.vertical, 10)
            }
            .background(
                Capsule().fill(BaoziTheme.accent)
            )
            .foregroundColor(BaoziTheme.textOnAccent)
        }
        .padding(.horizontal, 24)
    }

    // MARK: - Helpers

    private func handlePick(result: Result<[URL], Error>, mode: PickerMode?) {
        switch result {
        case .success(let urls):
            guard let url = urls.first else { return }
            Task {
                switch mode {
                case .reconnect(let id):
                    await store.reconnect(id: id, newUrl: url)
                case .add, .none:
                    await store.addByPicking(url: url)
                }
            }
        case .failure(let error):
            LLog.warn("mount", "file picker failed", fields: ["error": String(describing: error)])
        }
    }

    private func statusIcon(for status: MountStatus?) -> some View {
        let (symbol, tint): (String, Color) = {
            switch status {
            case .mounted:
                return ("checkmark.circle.fill", BaoziTheme.accent)
            case .resolutionFailed, .mountFailed:
                return ("exclamationmark.triangle.fill", BaoziTheme.danger)
            case nil:
                return ("circle.dotted", BaoziTheme.textMuted)
            }
        }()
        return Image(systemName: symbol)
            .foregroundColor(tint)
            .frame(width: 18)
    }

    private func statusDetail(for status: MountStatus?) -> String? {
        switch status {
        case .resolutionFailed(let message):
            return "Couldn't reach this folder: \(message)"
        case .mountFailed(let rc, let message):
            return "Mount failed (rc=\(rc)): \(message)"
        case .mounted, nil:
            return nil
        }
    }

    private func needsReconnect(_ status: MountStatus?) -> Bool {
        switch status {
        case .resolutionFailed, .mountFailed: return true
        case .mounted, nil: return false
        }
    }

    private var removalPrompt: String {
        if let pendingRemoval {
            return "Remove \(pendingRemoval.name)?"
        }
        return "Remove mount?"
    }
}

#if DEBUG
#Preview {
    MountedFoldersView()
}
#endif
