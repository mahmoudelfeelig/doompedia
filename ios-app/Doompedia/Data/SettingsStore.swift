import Combine
import Foundation

final class SettingsStore: ObservableObject {
    @Published private(set) var settings: UserSettings

    private let defaults: UserDefaults
    private let key = "doompedia_settings"

    init(defaults: UserDefaults = .standard) {
        self.defaults = defaults
        if let data = defaults.data(forKey: key),
           let decoded = try? JSONDecoder().decode(UserSettings.self, from: data) {
            settings = decoded
        } else {
            settings = UserSettings()
        }
    }

    func update(_ mutate: (inout UserSettings) -> Void) {
        var copy = settings
        mutate(&copy)
        settings = copy
        if let data = try? JSONEncoder().encode(copy) {
            defaults.set(data, forKey: key)
        }
    }
}
