import Foundation
import SwiftUI
import UIKit

struct PackOption: Identifiable, Hashable {
    let id: String
    let title: String
    let subtitle: String
    let downloadSize: String
    let installSize: String
    let manifestURL: String
    let available: Bool
    let articleCount: Int
    let shardCount: Int
    let includedTopics: [String]
    let removable: Bool
}

@MainActor
final class MainViewModel: ObservableObject {
    @Published var settings: UserSettings
    @Published var query: String = ""
    @Published var feed: [RankedCard] = []
    @Published var searchResults: [ArticleCard] = []
    @Published var folders: [SaveFolderSummary] = []
    @Published var selectedFolderID: Int64 = WikiRepository.defaultBookmarksFolderID
    @Published var savedCards: [ArticleCard] = []
    @Published var folderPickerCard: ArticleCard?
    @Published var folderPickerSelection: Set<Int64> = []
    @Published var isLoading: Bool = true
    @Published var isUpdatingPack: Bool = false
    @Published var updateProgress: PackUpdateProgress?
    @Published var message: String?
    @Published var packCatalog: [PackOption]
    @Published var effectiveFeedMode: FeedMode
    @Published var exploreReselectToken: Int = 0

    var colorScheme: ColorScheme? {
        switch settings.themeMode {
        case .system: return nil
        case .light: return .light
        case .dark: return .dark
        }
    }

    private let container: AppContainer
    private var thumbnailCache: [Int64: String?] = [:]

    init(container: AppContainer) {
        self.container = container
        self.settings = container.settingsStore.settings
        self.packCatalog = buildPackCatalog(customPacksJSON: container.settingsStore.settings.customPacksJSON)
        self.effectiveFeedMode = NetworkMonitor.shared.isOnline ? container.settingsStore.settings.feedMode : .offline

        Task {
            await refreshFeed()
            await refreshSaved()
        }
    }

    static func make() -> MainViewModel {
        do {
            return MainViewModel(container: try AppContainer())
        } catch {
            fatalError("Failed to create app container: \(error)")
        }
    }

    func refreshFeed(manual: Bool = false) async {
        isLoading = true
        defer { isLoading = false }

        effectiveFeedMode = resolvedFeedMode(requested: settings.feedMode)
        let previousTopID = feed.first?.card.pageId

        do {
            if effectiveFeedMode == .online {
                let remote = try await container.wikipediaAPIClient.fetchRandomSummaries(
                    language: settings.language,
                    count: 40
                )
                if !remote.isEmpty {
                    let rows = remote.map { article in
                        SeedRow(
                            page_id: article.pageID,
                            lang: article.lang,
                            title: article.title,
                            summary: article.summary,
                            wiki_url: article.wikiURL,
                            topic_key: inferTopic(title: article.title, summary: article.summary),
                            quality_score: 0.55,
                            is_disambiguation: false,
                            source_rev_id: article.sourceRevID,
                            updated_at: article.updatedAt,
                            aliases: []
                        )
                    }
                    try container.repository.cacheOnlineArticles(rows)
                    let candidates = rows.map { row in
                        ArticleCard(
                            pageId: row.page_id,
                            lang: row.lang,
                            title: row.title,
                            normalizedTitle: normalizeSearch(row.title),
                            summary: row.summary,
                            wikiURL: row.wiki_url,
                            topicKey: inferTopic(title: row.title, summary: row.summary),
                            qualityScore: row.quality_score,
                            isDisambiguation: row.is_disambiguation,
                            sourceRevId: row.source_rev_id,
                            updatedAt: row.updated_at,
                            bookmarked: false
                        )
                    }
                    let ranked = try container.repository.rankCandidates(
                        language: settings.language,
                        level: settings.personalizationLevel,
                        candidates: candidates
                    )
                    feed = applyManualTopShuffleIfNeeded(
                        ranked: ranked,
                        previousTopID: previousTopID,
                        manual: manual
                    )
                } else {
                    let ranked = try container.repository.loadFeed(
                        language: settings.language,
                        level: settings.personalizationLevel
                    )
                    feed = applyManualTopShuffleIfNeeded(
                        ranked: ranked,
                        previousTopID: previousTopID,
                        manual: manual
                    )
                }
                return
            }

            let ranked = try container.repository.loadFeed(
                language: settings.language,
                level: settings.personalizationLevel
            )
            feed = applyManualTopShuffleIfNeeded(
                ranked: ranked,
                previousTopID: previousTopID,
                manual: manual
            )
        } catch {
            message = "Failed to load feed: \(error.localizedDescription)"
        }
    }

    func handleExploreReselected() {
        exploreReselectToken += 1
    }

    func refreshSaved() async {
        do {
            let list = try container.repository.saveFolders()
            folders = list
            if !list.contains(where: { $0.folderId == selectedFolderID }) {
                selectedFolderID = list.first?.folderId ?? WikiRepository.defaultBookmarksFolderID
            }
            savedCards = try container.repository.savedCards(
                folderID: selectedFolderID,
                language: settings.language,
                readSort: settings.readSort
            )
        } catch {
            message = "Failed to load saved folders"
        }
    }

    func selectSavedFolder(_ folderID: Int64) async {
        selectedFolderID = folderID
        do {
            savedCards = try container.repository.savedCards(
                folderID: folderID,
                language: settings.language,
                readSort: settings.readSort
            )
        } catch {
            message = "Could not load selected folder"
        }
    }

    func createFolder(_ name: String) async {
        do {
            let created = try container.repository.createFolder(name: name)
            if !created {
                message = "Folder name is invalid or already exists"
                return
            }
            await refreshSaved()
            message = "Folder created"
        } catch {
            message = "Could not create folder"
        }
    }

    func deleteFolder(_ folderID: Int64) async {
        do {
            let deleted = try container.repository.deleteFolder(folderID: folderID)
            if !deleted {
                message = "This folder cannot be removed"
                return
            }
            await refreshSaved()
            message = "Folder removed"
        } catch {
            message = "Could not remove folder"
        }
    }

    func showFolderPicker(for card: ArticleCard) async {
        folderPickerCard = card
        do {
            folderPickerSelection = try container.repository.selectedFolderIDs(pageID: card.pageId)
                .subtracting([WikiRepository.defaultReadFolderID])
        } catch {
            folderPickerSelection = []
        }
    }

    func toggleFolderInPicker(_ folderID: Int64) {
        if folderID == WikiRepository.defaultReadFolderID { return }
        if folderPickerSelection.contains(folderID) {
            folderPickerSelection.remove(folderID)
        } else {
            folderPickerSelection.insert(folderID)
        }
    }

    func applyFolderPicker() async {
        guard let card = folderPickerCard else { return }
        do {
            try container.repository.setFoldersForArticle(
                pageID: card.pageId,
                folderIDs: folderPickerSelection
            )
            folderPickerCard = nil
            await refreshFeed()
            await refreshSaved()
            message = "Saved folders updated"
        } catch {
            message = "Could not update saved folders"
        }
    }

    func dismissFolderPicker() {
        folderPickerCard = nil
    }

    func updateQuery(_ value: String) {
        query = value
        Task { await runSearch(query: value) }
    }

    private func runSearch(query: String) async {
        let cleaned = query.trimmingCharacters(in: .whitespacesAndNewlines)
        if cleaned.isEmpty {
            searchResults = []
            return
        }

        do {
            if resolvedFeedMode(requested: settings.feedMode) == .online {
                let remote = try await container.wikipediaAPIClient.searchTitles(
                    language: settings.language,
                    query: cleaned,
                    limit: 25
                )
                if !remote.isEmpty {
                    let rows = remote.map { article in
                        SeedRow(
                            page_id: article.pageID,
                            lang: article.lang,
                            title: article.title,
                            summary: article.summary,
                            wiki_url: article.wikiURL,
                            topic_key: inferTopic(title: article.title, summary: article.summary),
                            quality_score: 0.55,
                            is_disambiguation: false,
                            source_rev_id: article.sourceRevID,
                            updated_at: article.updatedAt,
                            aliases: []
                        )
                    }
                    try container.repository.cacheOnlineArticles(rows)
                }
            }
            searchResults = try container.repository.search(language: settings.language, query: cleaned)
        } catch {
            message = "Search failed: \(error.localizedDescription)"
        }
    }

    func openCard(_ card: ArticleCard) async -> Bool {
        if !NetworkMonitor.shared.isOnline {
            message = "Full article requires a connection"
            return false
        }

        do {
            try container.repository.recordOpen(card: card, level: settings.personalizationLevel)
            if selectedFolderID == WikiRepository.defaultReadFolderID {
                await refreshSaved()
            } else {
                await refreshFeed()
            }
            return true
        } catch {
            message = "Could not track article open"
            return false
        }
    }

    func moreLike(_ card: ArticleCard) async {
        do {
            try container.repository.recordMoreLike(card: card, level: settings.personalizationLevel)
            let keys = CardKeywords.preferenceKeys(
                title: card.title,
                summary: card.summary,
                topicKey: card.topicKey,
                maxKeys: 10
            )
            let labels = keys.map { CardKeywords.prettyTopic($0) }.joined(separator: ", ")
            message = "Feed updated to show more like: \(labels)"
            await refreshFeed(manual: true)
        } catch {
            message = "Could not update recommendation preference"
        }
    }

    func lessLike(_ card: ArticleCard) async {
        do {
            try container.repository.recordLessLike(card: card, level: settings.personalizationLevel)
            let keys = CardKeywords.preferenceKeys(
                title: card.title,
                summary: card.summary,
                topicKey: card.topicKey,
                maxKeys: 10
            )
            let labels = keys.map { CardKeywords.prettyTopic($0) }.joined(separator: ", ")
            message = "Feed adjusted for less like: \(labels)"
            await refreshFeed(manual: true)
        } catch {
            message = "Could not update preferences"
        }
    }

    func toggleBookmark(_ card: ArticleCard) async {
        do {
            let saved = try container.repository.toggleBookmark(pageId: card.pageId)
            message = saved ? "Saved to Bookmarks" : "Removed from Bookmarks"
            await refreshFeed()
            await refreshSaved()
        } catch {
            message = "Could not update bookmark"
        }
    }

    func setPersonalization(_ level: PersonalizationLevel) {
        container.settingsStore.update { $0.personalizationLevel = level }
        syncSettingsFromStore()
        Task { await refreshFeed() }
    }

    func setFeedMode(_ mode: FeedMode) {
        container.settingsStore.update { $0.feedMode = mode }
        syncSettingsFromStore()
        if mode == .online && !NetworkMonitor.shared.isOnline {
            message = "No internet connection. Offline mode is active."
        }
        Task { await refreshFeed() }
    }

    func setTheme(_ mode: ThemeMode) {
        container.settingsStore.update { $0.themeMode = mode }
        syncSettingsFromStore()
    }

    func setAccentHex(_ hex: String) {
        container.settingsStore.update { $0.accentHex = hex.trimmingCharacters(in: .whitespacesAndNewlines) }
        syncSettingsFromStore()
    }

    func setFontScale(_ scale: Double) {
        container.settingsStore.update { $0.fontScale = min(max(scale, 0.85), 1.35) }
        syncSettingsFromStore()
    }

    func setHighContrast(_ enabled: Bool) {
        container.settingsStore.update { $0.highContrast = enabled }
        syncSettingsFromStore()
    }

    func setReduceMotion(_ enabled: Bool) {
        container.settingsStore.update { $0.reduceMotion = enabled }
        syncSettingsFromStore()
    }

    func setReadSort(_ sort: ReadSort) {
        container.settingsStore.update { $0.readSort = sort }
        syncSettingsFromStore()
        if selectedFolderID == WikiRepository.defaultReadFolderID {
            Task { await refreshSaved() }
        }
    }

    func setWifiOnly(_ enabled: Bool) {
        container.settingsStore.update { $0.wifiOnlyDownloads = enabled }
        syncSettingsFromStore()
    }

    func setDownloadPreviewImages(_ enabled: Bool) {
        container.settingsStore.update { $0.downloadPreviewImages = enabled }
        syncSettingsFromStore()
    }

    func setManifestURL(_ url: String) {
        container.settingsStore.update { $0.manifestURL = url.trimmingCharacters(in: .whitespacesAndNewlines) }
        syncSettingsFromStore()
    }

    func choosePack(_ pack: PackOption) {
        guard pack.available, !pack.manifestURL.isEmpty else { return }
        container.settingsStore.update { $0.manifestURL = pack.manifestURL }
        syncSettingsFromStore()
        message = "\(pack.title) selected (\(pack.articleCount) articles)"
    }

    func addPackByManifestURL(_ rawURL: String) async {
        let value = rawURL.trimmingCharacters(in: .whitespacesAndNewlines)
        if value.isEmpty {
            message = "Enter a manifest URL"
            return
        }
        guard let url = URL(string: value) else {
            message = "Invalid manifest URL"
            return
        }

        do {
            let (data, response) = try await URLSession.shared.data(from: url)
            guard let http = response as? HTTPURLResponse, (200 ... 299).contains(http.statusCode) else {
                message = "Manifest request failed"
                return
            }
            let manifest = try JSONDecoder().decode(PackManifest.self, from: data)
            container.settingsStore.update { current in
                current.customPacksJSON = upsertCustomPack(
                    existingJSON: current.customPacksJSON,
                    manifest: manifest,
                    manifestURL: value
                )
            }
            syncSettingsFromStore()
            message = "Pack added"
        } catch {
            message = "Could not add pack: \(error.localizedDescription)"
        }
    }

    func removePack(_ pack: PackOption) {
        guard pack.removable else { return }
        container.settingsStore.update { current in
            current.customPacksJSON = removeCustomPack(existingJSON: current.customPacksJSON, packID: pack.id)
            if current.manifestURL == pack.manifestURL {
                current.manifestURL = ""
            }
        }
        syncSettingsFromStore()
        message = "\(pack.title) removed"
    }

    func exportSettingsToClipboard() {
        guard let payload = container.settingsStore.exportJSONString(pretty: true) else {
            message = "Could not export settings"
            return
        }
        UIPasteboard.general.string = payload
        message = "Settings JSON copied to clipboard"
    }

    func importSettingsJSON(_ payload: String) async {
        do {
            _ = try container.settingsStore.importJSONString(payload)
            syncSettingsFromStore()
            await refreshFeed()
            await refreshSaved()
            message = "Settings imported"
        } catch {
            message = "Invalid settings JSON"
        }
    }

    func exportSelectedFolderToClipboard() {
        if selectedFolderID == WikiRepository.defaultReadFolderID {
            message = "Read activity cannot be exported"
            return
        }
        do {
            let payload = try container.repository.exportFolders(
                selectedFolderIDs: Set([selectedFolderID]),
                language: settings.language
            )
            UIPasteboard.general.string = payload
            message = "Selected folder JSON copied to clipboard"
        } catch {
            message = "Could not export selected folder"
        }
    }

    func exportAllFoldersToClipboard() {
        do {
            let payload = try container.repository.exportFolders(language: settings.language)
            UIPasteboard.general.string = payload
            message = "All folders JSON copied to clipboard"
        } catch {
            message = "Could not export folders"
        }
    }

    func importFoldersJSON(_ payload: String) async {
        do {
            let linked = try container.repository.importFolders(payload: payload)
            await refreshSaved()
            await refreshFeed()
            message = "Folder import complete (\(linked) links applied)"
        } catch {
            message = "Invalid folder JSON"
        }
    }

    func checkForUpdatesNow() async {
        if settings.manifestURL.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
            message = "Set a manifest URL first"
            return
        }

        isUpdatingPack = true
        updateProgress = PackUpdateProgress(
            phase: "Starting",
            percent: 0,
            downloadedBytes: 0,
            totalBytes: 0,
            bytesPerSecond: 0,
            detail: ""
        )
        defer { isUpdatingPack = false }

        let result = await container.updateService.checkAndApply(
            manifestURLString: settings.manifestURL,
            wifiOnly: settings.wifiOnlyDownloads,
            installedVersion: settings.installedPackVersion,
            onProgress: { [weak self] progress in
                Task { @MainActor in
                    self?.updateProgress = progress
                }
            }
        )

        container.settingsStore.update { current in
            current.installedPackVersion = result.installedVersion
            current.lastUpdateISO = ISO8601DateFormatter().string(from: Date())
            current.lastUpdateStatus = result.message
        }
        syncSettingsFromStore()
        message = result.message
        if result.status == .updated {
            await refreshFeed()
            await refreshSaved()
        }
    }

    func resolveThumbnailURL(for card: ArticleCard) async -> String? {
        if !settings.downloadPreviewImages { return nil }
        if abs(card.pageId) % 10 != 0 { return nil }
        if !NetworkMonitor.shared.isOnline { return nil }
        if let cached = thumbnailCache[card.pageId] {
            return cached
        }

        let fetched = try? await container.wikipediaAPIClient.fetchThumbnailURL(
            language: card.lang,
            title: card.title,
            maxWidth: 720
        )
        thumbnailCache[card.pageId] = fetched
        if thumbnailCache.count > 3000, let first = thumbnailCache.keys.first {
            thumbnailCache.removeValue(forKey: first)
        }
        return fetched ?? nil
    }

    private func resolvedFeedMode(requested: FeedMode) -> FeedMode {
        if requested == .online && !NetworkMonitor.shared.isOnline {
            return .offline
        }
        return requested
    }

    private func applyManualTopShuffleIfNeeded(
        ranked: [RankedCard],
        previousTopID: Int64?,
        manual: Bool
    ) -> [RankedCard] {
        guard
            manual,
            ranked.count > 1,
            let previousTopID,
            ranked.first?.card.pageId == previousTopID
        else {
            return ranked
        }
        return Array(ranked.dropFirst()) + [ranked[0]]
    }

    private func syncSettingsFromStore() {
        settings = container.settingsStore.settings
        packCatalog = buildPackCatalog(customPacksJSON: settings.customPacksJSON)
        effectiveFeedMode = resolvedFeedMode(requested: settings.feedMode)
    }
}

private func buildPackCatalog(customPacksJSON: String) -> [PackOption] {
    let defaults = [
        PackOption(
            id: "en-core-1m",
            title: "English Core 1M",
            subtitle: "General encyclopedia pack with biographies, science, geography, history, and culture.",
            downloadSize: "~380 MB (gzip) / ~396 MB raw",
            installSize: "~1.3 GB",
            manifestURL: "https://packs.example.invalid/packs/en-core-1m/v1/manifest.json",
            available: true,
            articleCount: 1_000_000,
            shardCount: 25,
            includedTopics: ["General", "Science", "History", "Geography", "Culture", "Biography"],
            removable: false
        ),
        PackOption(
            id: "en-science-250k",
            title: "English STEM Pack",
            subtitle: "Focused on science, technology, health, and environment topics.",
            downloadSize: "~1.2 MB (gzip)",
            installSize: "~20 MB",
            manifestURL: "https://packs.example.invalid/packs/en-science-250k/v1/manifest.json",
            available: true,
            articleCount: 16_811,
            shardCount: 1,
            includedTopics: ["Science", "Technology", "Health", "Environment"],
            removable: false
        ),
        PackOption(
            id: "en-history-250k",
            title: "English History & Society",
            subtitle: "Focused on history, biography, culture, and politics topics.",
            downloadSize: "~16 MB (gzip)",
            installSize: "~340 MB",
            manifestURL: "https://packs.example.invalid/packs/en-history-250k/v1/manifest.json",
            available: true,
            articleCount: 250_000,
            shardCount: 7,
            includedTopics: ["History", "Biography", "Culture", "Politics"],
            removable: false
        ),
        PackOption(
            id: "en-all-summaries",
            title: "English All Summaries",
            subtitle: "Largest available EN pack with all extracted short summaries.",
            downloadSize: "~384 MB (gzip)",
            installSize: "~6-9 GB",
            manifestURL: "https://packs.example.invalid/packs/en-all-summaries/v1/manifest.json",
            available: true,
            articleCount: 6_262_893,
            shardCount: 157,
            includedTopics: ["All"],
            removable: false
        )
    ]

    return defaults + parseCustomPacks(payload: customPacksJSON)
}

private func parseCustomPacks(payload: String) -> [PackOption] {
    guard
        let data = payload.data(using: .utf8),
        let root = try? JSONSerialization.jsonObject(with: data) as? [[String: Any]]
    else {
        return []
    }

    return root.compactMap { item in
        guard
            let id = (item["id"] as? String)?.trimmingCharacters(in: .whitespacesAndNewlines),
            let title = (item["title"] as? String)?.trimmingCharacters(in: .whitespacesAndNewlines),
            let manifestURL = (item["manifestUrl"] as? String)?.trimmingCharacters(in: .whitespacesAndNewlines),
            !id.isEmpty, !title.isEmpty, !manifestURL.isEmpty
        else { return nil }

        let subtitle = (item["subtitle"] as? String)?.trimmingCharacters(in: .whitespacesAndNewlines)
        let downloadSize = (item["downloadSize"] as? String)?.trimmingCharacters(in: .whitespacesAndNewlines)
        let installSize = (item["installSize"] as? String)?.trimmingCharacters(in: .whitespacesAndNewlines)
        let articleCount = (item["articleCount"] as? NSNumber)?.intValue ?? 0
        let shardCount = (item["shardCount"] as? NSNumber)?.intValue ?? 0
        let includedTopics = (item["includedTopics"] as? [String]) ?? []

        return PackOption(
            id: id,
            title: title,
            subtitle: subtitle?.isEmpty == false ? subtitle! : "Custom pack",
            downloadSize: downloadSize?.isEmpty == false ? downloadSize! : "Unknown",
            installSize: installSize?.isEmpty == false ? installSize! : "Unknown",
            manifestURL: manifestURL,
            available: true,
            articleCount: max(0, articleCount),
            shardCount: max(0, shardCount),
            includedTopics: includedTopics,
            removable: true
        )
    }
}

private func upsertCustomPack(existingJSON: String, manifest: PackManifest, manifestURL: String) -> String {
    let existingData = existingJSON.data(using: .utf8) ?? Data("[]".utf8)
    let existing = (try? JSONSerialization.jsonObject(with: existingData) as? [[String: Any]]) ?? []
    let filtered = existing.filter { ($0["id"] as? String) != manifest.packId }

    let topicList: [String]
    if let tags = manifest.packTags, !tags.isEmpty {
        topicList = tags.prefix(8).map(prettyTopic)
    } else {
        topicList = (manifest.topicDistribution ?? [:])
            .sorted { lhs, rhs in lhs.value > rhs.value }
            .prefix(8)
            .map { prettyTopic($0.key) }
    }

    let downloadBytes = manifest.shards.reduce(Int64(0)) { $0 + $1.bytes }
    let generated: [String: Any] = [
        "id": manifest.packId,
        "title": prettyTopic(manifest.packId),
        "subtitle": (manifest.description?.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty == false)
            ? manifest.description!
            : "Pack imported from manifest",
        "manifestUrl": manifestURL,
        "downloadSize": formatBytes(downloadBytes),
        "installSize": "Varies by device",
        "articleCount": manifest.recordCount,
        "shardCount": manifest.shards.count,
        "includedTopics": topicList,
    ]

    let output = filtered + [generated]
    let data = (try? JSONSerialization.data(withJSONObject: output, options: [.sortedKeys])) ?? Data("[]".utf8)
    return String(decoding: data, as: UTF8.self)
}

private func removeCustomPack(existingJSON: String, packID: String) -> String {
    let existingData = existingJSON.data(using: .utf8) ?? Data("[]".utf8)
    let existing = (try? JSONSerialization.jsonObject(with: existingData) as? [[String: Any]]) ?? []
    let filtered = existing.filter { ($0["id"] as? String) != packID }
    let data = (try? JSONSerialization.data(withJSONObject: filtered, options: [.sortedKeys])) ?? Data("[]".utf8)
    return String(decoding: data, as: UTF8.self)
}

private func formatBytes(_ bytes: Int64) -> String {
    if bytes <= 0 { return "Unknown" }
    let mb = Double(bytes) / (1024.0 * 1024.0)
    if mb >= 1024.0 {
        return String(format: "%.2f GB", mb / 1024.0)
    }
    return String(format: "%.0f MB", mb)
}

private func prettyTopic(_ raw: String) -> String {
    let normalized = raw
        .replacingOccurrences(of: "-", with: " ")
        .replacingOccurrences(of: "_", with: " ")
        .trimmingCharacters(in: .whitespacesAndNewlines)
    if normalized.isEmpty { return "General" }
    return normalized.capitalized
}

private func inferTopic(title: String, summary: String) -> String {
    let text = "\(title) \(summary)".lowercased()
    let rules: [(String, [String])] = [
        ("biography", ["born", "died", "actor", "author", "scientist", "politician", "player"]),
        ("history", ["empire", "war", "century", "kingdom", "revolution", "historical"]),
        ("science", ["physics", "chemistry", "biology", "mathematics", "astronomy", "scientific"]),
        ("technology", ["software", "computer", "internet", "digital", "algorithm", "device"]),
        ("geography", ["river", "mountain", "city", "country", "region", "province", "capital"]),
        ("politics", ["election", "government", "parliament", "minister", "policy", "party"]),
        ("economics", ["economy", "market", "trade", "finance", "currency", "industry"]),
        ("health", ["disease", "medical", "medicine", "health", "hospital", "symptom"]),
        ("sports", ["football", "basketball", "olympic", "league", "athlete", "championship"]),
        ("environment", ["climate", "ecology", "forest", "wildlife", "pollution", "conservation"]),
        ("culture", ["music", "film", "literature", "art", "religion", "language"]),
    ]

    for (topic, keywords) in rules where keywords.contains(where: { text.contains($0) }) {
        return topic
    }
    return "general"
}
