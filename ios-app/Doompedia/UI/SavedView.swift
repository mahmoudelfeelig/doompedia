import SwiftUI

struct SavedView: View {
    @ObservedObject var viewModel: MainViewModel
    @Environment(\.openURL) private var openURL
    @State private var newFolderName = ""
    @State private var importDraft = ""

    private var selectedFolder: SaveFolderSummary? {
        viewModel.folders.first { $0.folderId == viewModel.selectedFolderID }
    }

    private var isReadFolder: Bool {
        selectedFolder?.folderId == WikiRepository.defaultReadFolderID
    }

    var body: some View {
        NavigationStack {
            VStack(spacing: 10) {
                VStack(spacing: 8) {
                    HStack(spacing: 8) {
                        TextField("New folder", text: $newFolderName)
                            .textFieldStyle(.roundedBorder)
                        Button("Add") {
                            let current = newFolderName
                            newFolderName = ""
                            Task { await viewModel.createFolder(current) }
                        }
                        .buttonStyle(.bordered)
                        .disabled(newFolderName.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty)
                    }

                    ScrollView(.horizontal, showsIndicators: false) {
                        HStack(spacing: 8) {
                            ForEach(viewModel.folders) { folder in
                                Button {
                                    Task { await viewModel.selectSavedFolder(folder.folderId) }
                                } label: {
                                    Text("\(viewModel.selectedFolderID == folder.folderId ? "• " : "")\(folder.name) (\(folder.articleCount))")
                                        .font(.caption)
                                }
                                .buttonStyle(.bordered)
                            }
                        }
                    }

                    HStack(spacing: 8) {
                        Button("Export selected") {
                            viewModel.exportSelectedFolderToClipboard()
                        }
                        .buttonStyle(.bordered)

                        Button("Export all") {
                            viewModel.exportAllFoldersToClipboard()
                        }
                        .buttonStyle(.bordered)
                    }

                    TextEditor(text: $importDraft)
                        .frame(minHeight: 100)
                        .font(.system(.footnote, design: .monospaced))
                        .overlay(
                            RoundedRectangle(cornerRadius: 8)
                                .stroke(Color.secondary.opacity(0.2), lineWidth: 1)
                        )

                    Button("Import folders") {
                        let payload = importDraft
                        importDraft = ""
                        Task { await viewModel.importFoldersJSON(payload) }
                    }
                    .buttonStyle(.bordered)
                    .disabled(importDraft.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty)
                }
                .padding(.horizontal, 16)

                if isReadFolder {
                    Picker("Read sorting", selection: Binding(
                        get: { viewModel.settings.readSort },
                        set: { viewModel.setReadSort($0) }
                    )) {
                        Text("Latest").tag(ReadSort.newestFirst)
                        Text("Earliest").tag(ReadSort.oldestFirst)
                    }
                    .pickerStyle(.segmented)
                    .padding(.horizontal, 16)
                }

                if let selectedFolder, !selectedFolder.isDefault {
                    HStack {
                        Spacer()
                        Button("Remove folder", role: .destructive) {
                            Task { await viewModel.deleteFolder(selectedFolder.folderId) }
                        }
                        .buttonStyle(.borderless)
                    }
                    .padding(.horizontal, 16)
                }

                if viewModel.savedCards.isEmpty {
                    VStack(alignment: .leading, spacing: 6) {
                        Text(selectedFolder?.name ?? "Saved")
                            .font(.headline)
                        Text(
                            isReadFolder
                                ? "Read activity is empty. Open some cards from Explore first."
                                : "No saved articles in this folder yet."
                        )
                        .font(.subheadline)
                        .foregroundStyle(.secondary)
                    }
                    .padding(.horizontal, 16)
                    .frame(maxWidth: .infinity, alignment: .leading)
                } else {
                    List(viewModel.savedCards) { card in
                        VStack(alignment: .leading, spacing: 8) {
                            Text(card.title)
                                .font(.headline)
                            Text(card.summary)
                                .font(.subheadline)
                                .lineLimit(4)

                            HStack {
                                if viewModel.selectedFolderID == WikiRepository.defaultBookmarksFolderID {
                                    Button(card.bookmarked ? "Unsave" : "Save") {
                                        Task { await viewModel.toggleBookmark(card) }
                                    }
                                    .buttonStyle(.bordered)
                                }

                                if !isReadFolder {
                                    Button("Folders") {
                                        Task { await viewModel.showFolderPicker(for: card) }
                                    }
                                    .buttonStyle(.bordered)
                                }
                            }
                        }
                        .padding(.vertical, 4)
                        .contentShape(Rectangle())
                        .onTapGesture {
                            Task {
                                let shouldOpen = await viewModel.openCard(card)
                                if shouldOpen, let url = URL(string: card.wikiURL) {
                                    openURL(url)
                                }
                            }
                        }
                    }
                    .listStyle(.plain)
                    .refreshable {
                        await viewModel.refreshSaved()
                    }
                }
            }
            .navigationTitle("Saved")
        }
    }
}
