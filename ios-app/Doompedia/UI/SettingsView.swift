import SwiftUI

struct SettingsView: View {
    @ObservedObject var viewModel: MainViewModel
    @State private var manifestURLDraft: String = ""

    var body: some View {
        NavigationStack {
            Form {
                Section("Personalization") {
                    Picker("Level", selection: Binding(
                        get: { viewModel.settings.personalizationLevel },
                        set: { viewModel.setPersonalization($0) }
                    )) {
                        ForEach(PersonalizationLevel.allCases, id: \.self) { level in
                            Text(level.rawValue).tag(level)
                        }
                    }
                    .pickerStyle(.segmented)

                    Text("Adaptive ranking remains transparent and non-aggressive.")
                        .font(.footnote)
                        .foregroundStyle(.secondary)
                }

                Section("Appearance") {
                    Picker("Theme", selection: Binding(
                        get: { viewModel.settings.themeMode },
                        set: { viewModel.setTheme($0) }
                    )) {
                        ForEach(ThemeMode.allCases, id: \.self) { mode in
                            Text(mode.rawValue).tag(mode)
                        }
                    }
                }

                Section("Downloads") {
                    Toggle("Wi-Fi only", isOn: Binding(
                        get: { viewModel.settings.wifiOnlyDownloads },
                        set: { viewModel.setWifiOnly($0) }
                    ))

                    TextField("Manifest URL", text: Binding(
                        get: { manifestURLDraft },
                        set: { value in
                            manifestURLDraft = value
                            viewModel.setManifestURL(value)
                        }
                    ))
                    .textInputAutocapitalization(.never)
                    .autocorrectionDisabled()

                    Button(viewModel.isUpdatingPack ? "Checking..." : "Check updates now") {
                        Task { await viewModel.checkForUpdatesNow() }
                    }
                    .disabled(viewModel.isUpdatingPack)

                    Text("Installed pack version: \(viewModel.settings.installedPackVersion)")
                        .font(.footnote)
                        .foregroundStyle(.secondary)
                    if !viewModel.settings.lastUpdateISO.isEmpty {
                        Text("Last check: \(viewModel.settings.lastUpdateISO)")
                            .font(.footnote)
                            .foregroundStyle(.secondary)
                    }
                    if !viewModel.settings.lastUpdateStatus.isEmpty {
                        Text(viewModel.settings.lastUpdateStatus)
                            .font(.footnote)
                            .foregroundStyle(.secondary)
                    }
                }

                Section("Data") {
                    Text("Language: \(viewModel.settings.language)")
                    Text("No account required. Preferences remain on-device.")
                        .font(.footnote)
                        .foregroundStyle(.secondary)
                    Link("CC BY-SA 4.0 license", destination: URL(string: "https://creativecommons.org/licenses/by-sa/4.0/")!)
                }
            }
            .navigationTitle("Settings")
            .onAppear {
                if manifestURLDraft.isEmpty {
                    manifestURLDraft = viewModel.settings.manifestURL
                }
            }
            .onChange(of: viewModel.settings.manifestURL) { _, newValue in
                if newValue != manifestURLDraft {
                    manifestURLDraft = newValue
                }
            }
        }
    }
}
