import SwiftUI

struct ExperimentalFeaturesView: View {
    @State private var experimentalFeatures = ExperimentalFeatures.shared
    @State private var debugSettings = DebugSettings.shared

    var body: some View {
        ZStack {
            BaoziTheme.backgroundGradient.ignoresSafeArea()
            Form {
                Section {
                    ForEach(BaoziFeature.allCases) { feature in
                        Toggle(isOn: binding(for: feature)) {
                            VStack(alignment: .leading, spacing: 4) {
                                Text(feature.displayName)
                                    .baoziFont(.subheadline)
                                    .foregroundColor(BaoziTheme.textPrimary)
                                Text(feature.description)
                                    .baoziFont(.caption)
                                    .foregroundColor(BaoziTheme.textSecondary)
                            }
                        }
                        .tint(BaoziTheme.accentStrong)
                        .listRowBackground(BaoziTheme.surface.opacity(0.6))
                    }
                } header: {
                    Text("Features")
                        .foregroundColor(BaoziTheme.textSecondary)
                } footer: {
                    Text("Experimental features may be unstable or change without notice.")
                        .foregroundColor(BaoziTheme.textMuted)
                }

                Section {
                    Toggle(isOn: Binding(
                        get: { debugSettings.enabled },
                        set: { debugSettings.enabled = $0 }
                    )) {
                        HStack(spacing: 10) {
                            Image(systemName: "ant")
                                .foregroundColor(BaoziTheme.accent)
                                .frame(width: 20)
                            VStack(alignment: .leading, spacing: 2) {
                                Text("Debug Mode")
                                    .baoziFont(.subheadline)
                                    .foregroundColor(BaoziTheme.textPrimary)
                                Text("Show debug controls in conversations")
                                    .baoziFont(.caption)
                                    .foregroundColor(BaoziTheme.textSecondary)
                            }
                        }
                    }
                    .tint(BaoziTheme.accent)
                    .listRowBackground(BaoziTheme.surface.opacity(0.6))

                    #if DEBUG
                    NavigationLink {
                        ProximityPairView()
                    } label: {
                        HStack(spacing: 10) {
                            Image(systemName: "wave.3.right")
                                .foregroundColor(BaoziTheme.accent)
                                .frame(width: 20)
                            VStack(alignment: .leading, spacing: 2) {
                                Text("Pair")
                                    .baoziFont(.subheadline)
                                    .foregroundColor(BaoziTheme.textPrimary)
                                Text("Walk-up pairing with proximity + haptics")
                                    .baoziFont(.caption)
                                    .foregroundColor(BaoziTheme.textSecondary)
                            }
                        }
                    }
                    .listRowBackground(BaoziTheme.surface.opacity(0.6))
                    #endif

                    #if !targetEnvironment(macCatalyst) && DEBUG
                    NavigationLink {
                        UWBDebugView()
                    } label: {
                        HStack(spacing: 10) {
                            Image(systemName: "dot.radiowaves.left.and.right")
                                .foregroundColor(BaoziTheme.accent)
                                .frame(width: 20)
                            VStack(alignment: .leading, spacing: 2) {
                                Text("UWB Debug")
                                    .baoziFont(.subheadline)
                                    .foregroundColor(BaoziTheme.textPrimary)
                                Text("Live distance & direction to a paired Mac")
                                    .baoziFont(.caption)
                                    .foregroundColor(BaoziTheme.textSecondary)
                            }
                        }
                    }
                    .listRowBackground(BaoziTheme.surface.opacity(0.6))
                    #endif
                } header: {
                    Text("Debug")
                        .foregroundColor(BaoziTheme.textSecondary)
                }
            }
            .scrollContentBackground(.hidden)
        }
        .navigationTitle("Experimental")
        .navigationBarTitleDisplayMode(.inline)
    }

    private func binding(for feature: BaoziFeature) -> Binding<Bool> {
        Binding(
            get: { experimentalFeatures.isEnabled(feature) },
            set: { newValue in
                experimentalFeatures.setEnabled(feature, newValue)
            }
        )
    }
}

#if DEBUG
#Preview("Experimental Features") {
    NavigationStack {
        ExperimentalFeaturesView()
    }
}
#endif
