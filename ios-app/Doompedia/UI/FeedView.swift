import SwiftUI

private enum FeedSortOption: String, CaseIterable {
    case recommended = "Recommended"
    case titleAsc = "A-Z"
    case titleDesc = "Z-A"
}

struct FeedView: View {
    @ObservedObject var viewModel: MainViewModel
    @Environment(\.openURL) private var openURL

    @State private var whyMessage = ""
    @State private var showWhyAlert = false
    @State private var selectedSort: FeedSortOption = .recommended
    @State private var selectedFilter: String = "All"
    @State private var didInitialRefresh = false
    @State private var isAtTop = true
    @State private var listResetToken = 0

    private var items: [RankedCard] {
        if viewModel.query.isEmpty {
            return viewModel.feed
        }
        return viewModel.searchResults.map { card in
            RankedCard(card: card, score: 0, why: "Search match by title or alias")
        }
    }

    private var availableFilters: [String] {
        Array(Set(items.flatMap { buildTags(for: $0.card) }))
            .sorted()
            .prefix(14)
            .map { $0 }
    }

    private var visibleItems: [RankedCard] {
        var filtered = items
        if selectedFilter != "All" {
            filtered = filtered.filter { ranked in
                buildTags(for: ranked.card).contains(where: { $0.caseInsensitiveCompare(selectedFilter) == .orderedSame })
            }
        }
        switch selectedSort {
        case .recommended:
            return filtered
        case .titleAsc:
            return filtered.sorted { $0.card.title.localizedCaseInsensitiveCompare($1.card.title) == .orderedAscending }
        case .titleDesc:
            return filtered.sorted { $0.card.title.localizedCaseInsensitiveCompare($1.card.title) == .orderedDescending }
        }
    }

    var body: some View {
        NavigationStack {
            VStack(spacing: 10) {
                TextField("Search title", text: Binding(
                    get: { viewModel.query },
                    set: { viewModel.updateQuery($0) }
                ))
                .textFieldStyle(.roundedBorder)
                .padding(.horizontal, 16)
                .accessibilityLabel("Search by title")

                HStack(spacing: 12) {
                    Menu {
                        ForEach(FeedSortOption.allCases, id: \.self) { option in
                            Button(option.rawValue) { selectedSort = option }
                        }
                    } label: {
                        Label("Sort: \(selectedSort.rawValue)", systemImage: "line.3.horizontal.decrease.circle")
                    }

                    Menu {
                        Button("All") { selectedFilter = "All" }
                        ForEach(availableFilters, id: \.self) { filter in
                            Button(filter) { selectedFilter = filter }
                        }
                    } label: {
                        Label("Filter: \(selectedFilter)", systemImage: "tag")
                    }

                    Spacer()
                }
                .font(.subheadline)
                .padding(.horizontal, 16)

                if viewModel.isLoading {
                    ProgressView()
                        .padding(.top, 8)
                }

                List(visibleItems) { ranked in
                    VStack(alignment: .leading, spacing: 10) {
                        if viewModel.settings.downloadPreviewImages, shouldShowImage(for: ranked.card) {
                            RemoteArticleImage(
                                card: ranked.card,
                                viewModel: viewModel
                            )
                            .frame(maxWidth: .infinity)
                            .frame(height: 180)
                            .clipShape(RoundedRectangle(cornerRadius: 10))
                        }

                        HStack(alignment: .top, spacing: 8) {
                            Text(ranked.card.title)
                                .font(.headline)
                                .lineLimit(3)
                            Spacer(minLength: 8)
                            Button {
                                whyMessage = """
                                This card is shown using your personalization level, diversity guardrails, and controlled exploration.

                                \(ranked.why)
                                """
                                showWhyAlert = true
                            } label: {
                                Image(systemName: "info.circle")
                                    .font(.headline)
                            }
                            .buttonStyle(.plain)
                            .accessibilityLabel("Why this is shown")
                        }

                        ScrollView(.horizontal, showsIndicators: false) {
                            HStack(spacing: 6) {
                                ForEach(buildTags(for: ranked.card), id: \.self) { tag in
                                    Text(tag)
                                        .font(.caption2.weight(.semibold))
                                        .foregroundStyle(.secondary)
                                        .padding(.horizontal, 8)
                                        .padding(.vertical, 4)
                                        .background(Color.secondary.opacity(0.12))
                                        .clipShape(Capsule())
                                }
                            }
                        }

                        Text(ranked.card.summary)
                            .font(.subheadline)
                            .lineLimit(8)

                        ScrollView(.horizontal, showsIndicators: false) {
                            HStack(spacing: 8) {
                                Button {
                                    Task { await viewModel.showFolderPicker(for: ranked.card) }
                                } label: {
                                    Image(systemName: ranked.card.bookmarked ? "bookmark.fill" : "bookmark")
                                }
                                .buttonStyle(.bordered)
                                .labelStyle(.iconOnly)
                                .accessibilityLabel(ranked.card.bookmarked ? "Saved to folders" : "Save to folders")

                                Button {
                                    Task { await viewModel.moreLike(ranked.card) }
                                } label: {
                                    Image(systemName: "hand.thumbsup")
                                }
                                .buttonStyle(.bordered)
                                .labelStyle(.iconOnly)
                                .accessibilityLabel("Like this type of article")

                                Button {
                                    Task { await viewModel.lessLike(ranked.card) }
                                } label: {
                                    Image(systemName: "hand.thumbsdown")
                                }
                                .buttonStyle(.bordered)
                                .labelStyle(.iconOnly)
                                .accessibilityLabel("Dislike this type of article")
                            }
                        }
                    }
                    .padding(.vertical, 6)
                    .contentShape(Rectangle())
                    .onAppear {
                        if ranked.id == visibleItems.first?.id {
                            isAtTop = true
                        }
                    }
                    .onDisappear {
                        if ranked.id == visibleItems.first?.id {
                            isAtTop = false
                        }
                    }
                    .onTapGesture {
                        Task {
                            let shouldOpen = await viewModel.openCard(ranked.card)
                            if shouldOpen, let url = URL(string: ranked.card.wikiURL) {
                                openURL(url)
                            }
                        }
                    }
                }
                .id(listResetToken)
                .listStyle(.plain)
                .refreshable {
                    await viewModel.refreshFeed(manual: true)
                }
            }
            .navigationTitle("Doompedia")
            .onChange(of: viewModel.exploreReselectToken) { _, _ in
                if isAtTop {
                    Task { await viewModel.refreshFeed(manual: true) }
                } else {
                    listResetToken += 1
                    isAtTop = true
                }
            }
            .onAppear {
                guard !didInitialRefresh else { return }
                didInitialRefresh = true
                if viewModel.feed.isEmpty, viewModel.query.isEmpty {
                    Task { await viewModel.refreshFeed() }
                }
            }
            .alert("Why this is shown", isPresented: $showWhyAlert) {
                Button("Got it", role: .cancel) {}
            } message: {
                Text(whyMessage)
            }
        }
    }
}

struct FolderPickerSheet: View {
    @ObservedObject var viewModel: MainViewModel
    let card: ArticleCard

    var body: some View {
        NavigationStack {
            List(viewModel.folders.filter { $0.folderId != WikiRepository.defaultReadFolderID }) { folder in
                Button {
                    viewModel.toggleFolderInPicker(folder.folderId)
                } label: {
                    HStack {
                        Text("\(folder.name) (\(folder.articleCount))")
                        Spacer()
                        if viewModel.folderPickerSelection.contains(folder.folderId) {
                            Image(systemName: "checkmark.circle.fill")
                                .foregroundStyle(.green)
                        }
                    }
                }
                .buttonStyle(.plain)
            }
            .navigationTitle("Save to folders")
            .toolbar {
                ToolbarItem(placement: .topBarLeading) {
                    Button("Cancel") {
                        viewModel.dismissFolderPicker()
                    }
                }
                ToolbarItem(placement: .topBarTrailing) {
                    Button("Apply") {
                        Task { await viewModel.applyFolderPicker() }
                    }
                }
            }
        }
    }
}

private func buildTags(for card: ArticleCard) -> [String] {
    var tags = CardKeywords.displayTags(
        title: card.title,
        summary: card.summary,
        topicKey: card.topicKey,
        bookmarked: card.bookmarked,
        maxTags: 6
    )
    if card.updatedAt.hasPrefix("1970-") {
        tags.append("Offline Pack")
    } else {
        tags.append("Live Cache")
    }
    return Array(tags.prefix(6))
}

private func shouldShowImage(for card: ArticleCard) -> Bool {
    return abs(card.pageId) % 10 == 0
}

private struct RemoteArticleImage: View {
    let card: ArticleCard
    @ObservedObject var viewModel: MainViewModel
    @State private var imageURL: String?

    var body: some View {
        Group {
            if let imageURL, let url = URL(string: imageURL) {
                AsyncImage(url: url) { phase in
                    switch phase {
                    case .empty:
                        ProgressView()
                    case .success(let image):
                        image
                            .resizable()
                            .scaledToFill()
                    case .failure:
                        EmptyView()
                    @unknown default:
                        EmptyView()
                    }
                }
            } else {
                EmptyView()
            }
        }
        .task(id: card.pageId) {
            imageURL = await viewModel.resolveThumbnailURL(for: card)
        }
    }
}
