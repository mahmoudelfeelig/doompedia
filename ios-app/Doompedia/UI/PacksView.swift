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

                    Toggle("Download preview images (10% sample)", isOn: Binding(
                        get: { viewModel.settings.downloadPreviewImages },
                        set: { viewModel.setDownloadPreviewImages($0) }
                    ))

                    Text("Installed pack version: \(viewModel.settings.installedPackVersion)")
                        .font(.footnote)
                        .foregroundStyle(.secondary)
                    if let progress = viewModel.updateProgress {
                        VStack(alignment: .leading, spacing: 6) {
                            Text("\(progress.phase) \(progress.detail)".trimmingCharacters(in: .whitespaces))
                                .font(.footnote)
                                .foregroundStyle(.secondary)
                            ProgressView(value: progress.percent / 100.0)
                            let bytesText: String = {
                                if progress.totalBytes > 0 {
                                    return "\(formatBytes(progress.downloadedBytes)) / \(formatBytes(progress.totalBytes))"
                                }
                                return formatBytes(progress.downloadedBytes)
                            }()
                            let speedText = progress.bytesPerSecond > 0 ? " • \(formatBytes(progress.bytesPerSecond))/s" : ""
                            Text(String(format: "%.1f%% • %@", progress.percent, bytesText) + speedText)
                                .font(.footnote)
                                .foregroundStyle(.secondary)
                        }
                    }
                    if !viewModel.settings.lastUpdateISO.isEmpty {
                        Text("Last checked: \(friendlyUpdateDate(viewModel.settings.lastUpdateISO))")
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

private func friendlyUpdateDate(_ iso: String) -> String {
    guard let date = ISO8601DateFormatter().date(from: iso) else { return iso }
    let formatter = DateFormatter()
    formatter.locale = Locale.current
    formatter.dateStyle = .medium
    formatter.timeStyle = .short
    return formatter.string(from: date)
}

private func formatBytes(_ bytes: Int64) -> String {
    if bytes <= 0 { return "0 B" }
    let kb = Double(bytes) / 1024.0
    if kb < 1024.0 { return String(format: "%.0f KB", kb) }
    let mb = kb / 1024.0
    if mb < 1024.0 { return String(format: "%.1f MB", mb) }
    return String(format: "%.2f GB", mb / 1024.0)
}
