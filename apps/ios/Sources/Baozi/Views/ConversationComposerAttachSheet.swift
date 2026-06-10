import SwiftUI

struct ConversationComposerAttachSheet: View {
    let onPickPhotoLibrary: () -> Void
    let onChooseFile: (() -> Void)?
    let onTakePhoto: (() -> Void)?

    var body: some View {
        VStack(spacing: 12) {
            Text("Attach")
                .baoziFont(.headline, weight: .semibold)
                .foregroundColor(BaoziTheme.textPrimary)
                .frame(maxWidth: .infinity, alignment: .leading)

            Button(action: onPickPhotoLibrary) {
                sheetButtonLabel("Photo Library", systemImage: "photo.on.rectangle")
            }

            if let onChooseFile {
                Button(action: onChooseFile) {
                    sheetButtonLabel("Choose File", systemImage: "folder")
                }
            }

            if let onTakePhoto {
                Button(action: onTakePhoto) {
                    sheetButtonLabel("Take Photo", systemImage: "camera")
                }
            }

            Spacer(minLength: 0)
        }
        .padding(.horizontal, 16)
        .padding(.top, 12)
        .padding(.bottom, 20)
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .top)
        .background(BaoziTheme.backgroundGradient.ignoresSafeArea())
    }

    @ViewBuilder
    private func sheetButtonLabel(_ title: String, systemImage: String) -> some View {
        HStack(spacing: 10) {
            Image(systemName: systemImage)
                .baoziFont(.body, weight: .medium)
                .foregroundColor(BaoziTheme.accent)
                .frame(width: 20)

            Text(title)
                .baoziFont(.body, weight: .medium)
                .foregroundColor(BaoziTheme.textPrimary)

            Spacer()
        }
        .padding(.horizontal, 16)
        .frame(height: 52)
        .modifier(GlassRoundedRectModifier(cornerRadius: 18))
    }
}
