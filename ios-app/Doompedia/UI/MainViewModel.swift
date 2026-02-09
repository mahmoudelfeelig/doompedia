import Foundation
import SwiftUI

@MainActor
final class MainViewModel: ObservableObject {
    @Published var settings: UserSettings
    @Published var query: String = ""
    @Published var feed: [RankedCard] = []
    @Published var searchResults: [ArticleCard] = []
    @Published var isLoading: Bool = true
    @Published var isUpdatingPack: Bool = false
    @Published var message: String?

    var colorScheme: ColorScheme? {
        switch settings.themeMode {
        case .system:
            return nil
        case .light:
            return .light
        case .dark:
            return .dark
        }
    }

    private let container: AppContainer

    init(container: AppContainer) {
        self.container = container
        self.settings = container.settingsStore.settings

        Task {
            await refreshFeed()
            await maybeRunAutoUpdate()
        }
    }

    static func make() -> MainViewModel {
        do {
            return MainViewModel(container: try AppContainer())
        } catch {
            fatalError("Failed to create app container: \(error)")
        }
    }

    func refreshFeed() async {
        isLoading = true
        defer { isLoading = false }

        do {
            feed = try container.repository.loadFeed(
                language: settings.language,
                level: settings.personalizationLevel
            )
        } catch {
            message = "Failed to load feed: \(error.localizedDescription)"
        }
    }

    func updateQuery(_ value: String) {
        query = value
        if value.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
            searchResults = []
            return
        }

        do {
            searchResults = try container.repository.search(language: settings.language, query: value)
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
            await refreshFeed()
            return true
        } catch {
            message = "Could not track article open"
            return false
        }
    }

    func lessLike(_ card: ArticleCard) async {
        do {
            try container.repository.recordLessLike(card: card, level: settings.personalizationLevel)
            message = "Feed adjusted for less \(card.topicKey)"
            await refreshFeed()
        } catch {
            message = "Could not update preferences"
        }
    }

    func toggleBookmark(_ card: ArticleCard) async {
        do {
            let saved = try container.repository.toggleBookmark(pageId: card.pageId)
            message = saved ? "Saved bookmark" : "Removed bookmark"
            await refreshFeed()
        } catch {
            message = "Could not update bookmark"
        }
    }

    func setPersonalization(_ level: PersonalizationLevel) {
        container.settingsStore.update { $0.personalizationLevel = level }
        settings = container.settingsStore.settings
        Task { await refreshFeed() }
    }

    func setTheme(_ mode: ThemeMode) {
        container.settingsStore.update { $0.themeMode = mode }
        settings = container.settingsStore.settings
    }

    func setWifiOnly(_ enabled: Bool) {
        container.settingsStore.update { $0.wifiOnlyDownloads = enabled }
        settings = container.settingsStore.settings
    }

    func setManifestURL(_ url: String) {
        container.settingsStore.update { $0.manifestURL = url.trimmingCharacters(in: .whitespacesAndNewlines) }
        settings = container.settingsStore.settings
    }

    func checkForUpdatesNow(silent: Bool = false) async {
        if settings.manifestURL.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
            if !silent {
                message = "Set a manifest URL first"
            }
            return
        }

        isUpdatingPack = true
        defer { isUpdatingPack = false }

        let result = await container.updateService.checkAndApply(
            manifestURLString: settings.manifestURL,
            wifiOnly: settings.wifiOnlyDownloads,
            installedVersion: settings.installedPackVersion
        )

        container.settingsStore.update { current in
            current.installedPackVersion = result.installedVersion
            current.lastUpdateISO = ISO8601DateFormatter().string(from: Date())
            current.lastUpdateStatus = result.message
        }
        settings = container.settingsStore.settings
        if !silent || result.status == .failed {
            message = result.message
        }
        if result.status == .updated || !silent {
            await refreshFeed()
        }
    }

    private func maybeRunAutoUpdate() async {
        guard !settings.manifestURL.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else { return }
        guard shouldCheckUpdatesNow(lastCheckISO: settings.lastUpdateISO) else { return }
        await checkForUpdatesNow(silent: true)
    }

    private func shouldCheckUpdatesNow(lastCheckISO: String) -> Bool {
        guard !lastCheckISO.isEmpty else { return true }
        let parser = ISO8601DateFormatter()
        guard let previous = parser.date(from: lastCheckISO) else { return true }
        return Date().timeIntervalSince(previous) >= 6 * 60 * 60
    }
}
