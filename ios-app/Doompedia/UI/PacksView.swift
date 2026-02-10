import SwiftUI

struct PacksView: View {
    @ObservedObject var viewModel: MainViewModel
    @State private var manifestURLDraft = ""
    @State private var addManifestURLDraft = ""

    var body: some View {
        NavigationStack {
            Form {
                Section("Choose a pack") {
                    ForEach(viewModel.packCatalog) { pack in
                        VStack(alignment: .leading, spacing: 8) {
                            Text(pack.title)
                                .font(.headline)
                            Text(pack.subtitle)
                                .font(.footnote)
                                .foregroundStyle(.secondary)
                            Text("Articles: \(pack.articleCount) • Shards: \(pack.shardCount)")
                                .font(.caption)
                                .foregroundStyle(.secondary)
                            Text("Download: \(pack.downloadSize) • Installed: \(pack.installSize)")
                                .font(.caption)
                                .foregroundStyle(.secondary)
                            if !pack.includedTopics.isEmpty {
                                Text("Includes: \(pack.includedTopics.prefix(8).joined(separator: ", "))")
                                    .font(.caption)
                                    .foregroundStyle(.secondary)
                            }

                            HStack {
                                Button(pack.available ? "Use pack" : "Coming soon") {
                                    viewModel.choosePack(pack)
                                }
                                .disabled(!pack.available || pack.manifestURL.isEmpty)

                                if pack.removable {
                                    Button("Remove", role: .destructive) {
                                        viewModel.removePack(pack)
                                    }
                                    .disabled(viewModel.isUpdatingPack)
                                }
                            }
                        }
                        .padding(.vertical, 4)
                    }
                }

                Section("Add pack by manifest URL") {
                    TextField("https://packs.example.com/packs/<id>/v1/manifest.json", text: $addManifestURLDraft)
                        .textInputAutocapitalization(.never)
                        .autocorrectionDisabled()

                    Button("Add pack") {
                        let value = addManifestURLDraft
                        addManifestURLDraft = ""
                        Task { await viewModel.addPackByManifestURL(value) }
                    }
                    .disabled(addManifestURLDraft.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty || viewModel.isUpdatingPack)
                }

                Section("Manifest URL (advanced)") {
                    TextField("Manifest URL", text: Binding(
                        get: { manifestURLDraft },
                        set: { value in
                            manifestURLDraft = value
                            viewModel.setManifestURL(value)
                        }
                    ))
                    .textInputAutocapitalization(.never)
                    .autocorrectionDisabled()
                }

                Section("Updates") {
                    Button(viewModel.isUpdatingPack ? "Checking..." : "Download / update") {
                        Task { await viewModel.checkForUpdatesNow() }
                    }
                    .disabled(viewModel.isUpdatingPack)

                    Text("Installed pack version: \(viewModel.settings.installedPackVersion)")
                        .font(.footnote)
                        .foregroundStyle(.secondary)
                    if !viewModel.settings.lastUpdateISO.isEmpty {
                        Text("Last checked at: \(viewModel.settings.lastUpdateISO)")
                            .font(.footnote)
                            .foregroundStyle(.secondary)
                    }
                    if !viewModel.settings.lastUpdateStatus.isEmpty {
                        Text(viewModel.settings.lastUpdateStatus)
                            .font(.footnote)
                            .foregroundStyle(.secondary)
                    }
                }
            }
            .navigationTitle("Packs")
            .onAppear {
                if manifestURLDraft.isEmpty {
                    manifestURLDraft = viewModel.settings.manifestURL
                }
            }
            .onChange(of: viewModel.settings.manifestURL) { _, newValue in
                if manifestURLDraft != newValue {
                    manifestURLDraft = newValue
                }
            }
        }
    }
}
