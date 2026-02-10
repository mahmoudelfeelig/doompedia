import Foundation
import SwiftUI
import UIKit

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

    func refreshSaved() async {
        do {
            let list = try container.repository.saveFolders()
            folders = list
            if !list.contains(where: { $0.folderId == selectedFolderID }) {
                selectedFolderID = list.first?.folderId ?? WikiRepository.defaultBookmarksFolderID
            }
            savedCards = try container.repository.savedCards(folderID: selectedFolderID)
        } catch {
            message = "Failed to load saved folders"
        }
    }

    func selectSavedFolder(_ folderID: Int64) async {
        selectedFolderID = folderID
        do {
            savedCards = try container.repository.savedCards(folderID: folderID)
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
        } catch {
            folderPickerSelection = []
        }
    }

    func toggleFolderInPicker(_ folderID: Int64) {
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

    func moreLike(_ card: ArticleCard) async {
        do {
            try container.repository.recordMoreLike(card: card, level: settings.personalizationLevel)
            message = "Feed updated to show more \(card.topicKey)"
            await refreshFeed()
        } catch {
            message = "Could not update recommendation preference"
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
            message = saved ? "Saved to Bookmarks" : "Removed from Bookmarks"
            await refreshFeed()
            await refreshSaved()
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

    func setAccentHex(_ hex: String) {
        container.settingsStore.update { $0.accentHex = hex.trimmingCharacters(in: .whitespacesAndNewlines) }
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
            settings = container.settingsStore.settings
            await refreshFeed()
            await refreshSaved()
            message = "Settings imported"
        } catch {
            message = "Invalid settings JSON"
        }
    }

    func checkForUpdatesNow() async {
        if settings.manifestURL.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
            message = "Set a manifest URL first"
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
        message = result.message
        if result.status == .updated {
            await refreshFeed()
            await refreshSaved()
        }
    }
}
