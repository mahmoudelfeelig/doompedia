import Foundation

@MainActor
final class AppContainer: ObservableObject {
    let repository: WikiRepository
    let updateService: PackUpdateService
    let settingsStore: SettingsStore
    let wikipediaAPIClient: WikipediaAPIClient

    init() throws {
        URLCache.shared.memoryCapacity = 256 * 1024 * 1024
        URLCache.shared.diskCapacity = 3 * 1024 * 1024 * 1024

        let config = try RankingConfigLoader.load()
        let store = try SQLiteStore()
        let repository = WikiRepository(store: store, config: config)
        do {
            try repository.bootstrap()
        } catch {
            // Keep app startup resilient if bundled seed schema drifts.
            try store.ensureSaveDefaults()
        }
        let installer = PackInstaller(store: store)
        let deltaApplier = DeltaApplier(store: store)
        let downloader = ShardDownloader()
        let updateService = PackUpdateService(
            installer: installer,
            deltaApplier: deltaApplier,
            downloader: downloader
        )

        self.repository = repository
        self.updateService = updateService
        self.settingsStore = SettingsStore()
        self.wikipediaAPIClient = WikipediaAPIClient()
    }
}
