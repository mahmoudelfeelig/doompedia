import SwiftUI

struct SavedView: View {
    @ObservedObject var viewModel: MainViewModel
    @Environment(\.openURL) private var openURL
    @State private var newFolderName = ""

    var body: some View {
        NavigationStack {
            VStack(spacing: 10) {
                HStack(spacing: 8) {
                    TextField("New folder", text: $newFolderName)
                        .textFieldStyle(.roundedBorder)
                    Button("Add") {
                        let current = newFolderName
                        newFolderName = ""
                        Task { await viewModel.createFolder(current) }
                    }
                    .buttonStyle(.bordered)
                }
                .padding(.horizontal, 16)

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
                    .padding(.horizontal, 16)
                }

                if let selectedFolder = viewModel.folders.first(where: { $0.folderId == viewModel.selectedFolderID }),
                   !selectedFolder.isDefault {
                    HStack {
                        Spacer()
                        Button("Remove folder", role: .destructive) {
                            Task { await viewModel.deleteFolder(selectedFolder.folderId) }
                        }
                        .buttonStyle(.borderless)
                    }
                    .padding(.horizontal, 16)
                }

                List(viewModel.savedCards) { card in
                    VStack(alignment: .leading, spacing: 8) {
                        Text(card.title)
                            .font(.headline)
                        Text(card.summary)
                            .font(.subheadline)
                            .lineLimit(3)

                        HStack {
                            if viewModel.selectedFolderID == WikiRepository.defaultBookmarksFolderID {
                                Button(card.bookmarked ? "Unsave" : "Save") {
                                    Task { await viewModel.toggleBookmark(card) }
                                }
                                .buttonStyle(.bordered)
                            }

                            Button("Folders") {
                                Task { await viewModel.showFolderPicker(for: card) }
                            }
                            .buttonStyle(.bordered)
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
            .navigationTitle("Saved")
        }
    }
}
