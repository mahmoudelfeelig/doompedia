import SwiftUI

struct FeedView: View {
    @ObservedObject var viewModel: MainViewModel
    @Environment(\.openURL) private var openURL

    private var items: [RankedCard] {
        if viewModel.query.isEmpty {
            return viewModel.feed
        }
        return viewModel.searchResults.map { card in
            RankedCard(card: card, score: 0, why: "Search match")
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
                        HStack(spacing: 8) {
                            Text(ranked.card.topicKey.uppercased())
                                .font(.caption.weight(.semibold))
                                .foregroundStyle(.secondary)
                            if ranked.card.bookmarked {
                                Text("Saved")
                                    .font(.caption)
                                    .foregroundStyle(.secondary)
                            }
                        }

                        Text(ranked.card.title)
                            .font(.headline)

                        Text(ranked.card.summary)
                            .font(.subheadline)
                            .lineLimit(4)

                        Text(ranked.why)
                            .font(.footnote)
                            .foregroundStyle(.secondary)
                            .lineLimit(2)

                        HStack {
                            Button("Open") {
                                Task {
                                    let shouldOpen = await viewModel.openCard(ranked.card)
                                    if shouldOpen, let url = URL(string: ranked.card.wikiURL) {
                                        openURL(url)
                                    }
                                }
                            }
                            .buttonStyle(.borderedProminent)

                            Button(ranked.card.bookmarked ? "Unsave" : "Save") {
                                Task { await viewModel.toggleBookmark(ranked.card) }
                            }
                            .buttonStyle(.bordered)

                            Button("Less like this") {
                                Task { await viewModel.lessLike(ranked.card) }
                            }
                            .buttonStyle(.borderless)
                        }
                    }
                    .padding(.vertical, 6)
                }
                .listStyle(.plain)
                .refreshable {
                    await viewModel.refreshFeed()
                }
            }
            .navigationTitle("Doompedia")
        }
    }
}
