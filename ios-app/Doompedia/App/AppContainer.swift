import Foundation

@MainActor
final class AppContainer: ObservableObject {
    let repository: WikiRepository
    let updateService: PackUpdateService
    let settingsStore: SettingsStore

    init() throws {
        let config = try RankingConfigLoader.load()
        let store = try SQLiteStore()
        let repository = WikiRepository(store: store, config: config)
        try repository.bootstrap()
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
    }
}
