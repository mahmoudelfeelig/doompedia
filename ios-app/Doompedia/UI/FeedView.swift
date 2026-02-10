import SwiftUI

struct FeedView: View {
    @ObservedObject var viewModel: MainViewModel
    @Environment(\.openURL) private var openURL

    @State private var whyMessage: String = ""
    @State private var showWhyAlert = false

    private var items: [RankedCard] {
        if viewModel.query.isEmpty {
            return viewModel.feed
        }
        return viewModel.searchResults.map { card in
            RankedCard(card: card, score: 0, why: "Search match by title or alias")
        }
    }

    var body: some View {
        NavigationStack {
            VStack(spacing: 12) {
                TextField("Search title", text: Binding(
                    get: { viewModel.query },
                    set: { viewModel.updateQuery($0) }
                ))
                .textFieldStyle(.roundedBorder)
                .padding(.horizontal, 16)
                .accessibilityLabel("Search by title")

                if viewModel.isLoading {
                    ProgressView()
                        .padding(.top, 16)
                }

                List(items) { ranked in
                    VStack(alignment: .leading, spacing: 8) {
                        HStack {
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
                            Button("i") {
                                whyMessage = """
                                This recommendation is based on your recent reading behavior, diversity rules, and controlled exploration.

                                \(ranked.why)
                                """
                                showWhyAlert = true
                            }
                            .font(.caption.weight(.bold))
                        }

                        Text(ranked.card.title)
                            .font(.headline)

                        Text(ranked.card.summary)
                            .font(.subheadline)
                            .lineLimit(4)

                        HStack {
                            Button(ranked.card.bookmarked ? "Unsave" : "Save") {
                                Task { await viewModel.toggleBookmark(ranked.card) }
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

                            Button("Folders") {
                                Task { await viewModel.showFolderPicker(for: ranked.card) }
                            }
                            .buttonStyle(.bordered)
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
            List(viewModel.folders) { folder in
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
    let words = card.summary.split(whereSeparator: { $0.isWhitespace }).count
    let readMins = max(1, Int(ceil(Double(words) / 220.0)))
    let updatedYear: String? = {
        let prefix = String(card.updatedAt.prefix(4))
        return prefix.allSatisfy(\.isNumber) ? prefix : nil
    }()
    let qualityTag: String
    switch card.qualityScore {
    case 0.85...:
        qualityTag = "High quality"
    case 0.65...:
        qualityTag = "Solid quality"
    default:
        qualityTag = "Fresh pick"
    }

    var tags: [String] = [
        prettyTopic(card.topicKey),
        card.lang.uppercased(),
        "\(readMins)m read",
        qualityTag,
    ]
    if let updatedYear { tags.append(updatedYear) }
    if card.bookmarked { tags.append("Bookmarked") }
    if card.isDisambiguation { tags.append("Disambiguation") }
    return Array(tags.prefix(6))
}

private func prettyTopic(_ raw: String) -> String {
    let normalized = raw
        .replacingOccurrences(of: "-", with: " ")
        .replacingOccurrences(of: "_", with: " ")
        .trimmingCharacters(in: .whitespacesAndNewlines)
    if normalized.isEmpty { return "General" }
    return normalized.capitalized
}
