import SwiftUI

private enum FeedSortOption: String, CaseIterable {
    case recommended = "Recommended"
    case titleAsc = "A-Z"
    case titleDesc = "Z-A"
    case quality = "Quality"
}

struct FeedView: View {
    @ObservedObject var viewModel: MainViewModel
    @Environment(\.openURL) private var openURL

    @State private var whyMessage = ""
    @State private var showWhyAlert = false
    @State private var selectedSort: FeedSortOption = .recommended
    @State private var selectedFilter: String = "All"
    @State private var didInitialRefresh = false

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
        case .quality:
            return filtered.sorted { $0.card.qualityScore > $1.card.qualityScore }
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
                                Button(ranked.card.bookmarked ? "Saved" : "Save") {
                                    Task { await viewModel.showFolderPicker(for: ranked.card) }
                                }
                                .buttonStyle(.bordered)

                                Button("Show more") {
                                    Task { await viewModel.moreLike(ranked.card) }
                                }
                                .buttonStyle(.bordered)

                                Button("Show less") {
                                    Task { await viewModel.lessLike(ranked.card) }
                                }
                                .buttonStyle(.bordered)

                                if ranked.card.bookmarked {
                                    Button("Unsave") {
                                        Task { await viewModel.toggleBookmark(ranked.card) }
                                    }
                                    .buttonStyle(.bordered)
                                }
                            }
                        }
                    }
                    .padding(.vertical, 6)
                    .contentShape(Rectangle())
                    .onTapGesture {
                        Task {
                            let shouldOpen = await viewModel.openCard(ranked.card)
                            if shouldOpen, let url = URL(string: ranked.card.wikiURL) {
                                openURL(url)
                            }
                        }
                    }
                }
                .listStyle(.plain)
                .refreshable {
                    await viewModel.refreshFeed()
                }
            }
            .navigationTitle("Doompedia")
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
    let text = "\(card.title) \(card.summary)".lowercased()
    var tags = OrderedSet<String>()
    tags.append(prettyTopic(card.topicKey))

    let rules: [(String, [String])] = [
        ("Biography", ["born", "died", "actor", "author", "scientist", "politician"]),
        ("Science", ["physics", "chemistry", "biology", "mathematics", "astronomy"]),
        ("Technology", ["software", "computer", "internet", "digital", "algorithm"]),
        ("History", ["war", "empire", "century", "historical", "revolution"]),
        ("Geography", ["city", "country", "region", "river", "mountain", "capital"]),
        ("Politics", ["government", "election", "parliament", "policy", "minister"]),
        ("Culture", ["music", "film", "literature", "art", "religion", "language"]),
        ("Economics", ["economy", "finance", "market", "trade", "industry"]),
        ("Health", ["medicine", "disease", "medical", "health", "hospital"]),
    ]

    for (tag, keywords) in rules where keywords.contains(where: { text.contains($0) }) {
        tags.append(tag)
    }
    if card.title.localizedCaseInsensitiveContains("list of") { tags.append("Lists") }
    if card.title.localizedCaseInsensitiveContains("university") { tags.append("Education") }
    if card.bookmarked { tags.append("Saved") }

    return Array(tags.prefix(6))
}

private struct OrderedSet<Element: Hashable> {
    private var seen = Set<Element>()
    private(set) var ordered: [Element] = []

    mutating func append(_ value: Element) {
        guard seen.insert(value).inserted else { return }
        ordered.append(value)
    }

    func prefix(_ maxLength: Int) -> ArraySlice<Element> {
        ordered.prefix(maxLength)
    }
}

private func prettyTopic(_ raw: String) -> String {
    let normalized = raw
        .replacingOccurrences(of: "-", with: " ")
        .replacingOccurrences(of: "_", with: " ")
        .trimmingCharacters(in: .whitespacesAndNewlines)
    if normalized.isEmpty { return "General" }
    return normalized.capitalized
}
