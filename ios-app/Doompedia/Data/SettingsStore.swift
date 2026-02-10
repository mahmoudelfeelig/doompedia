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
        persist(copy)
    }

    func exportJSONString(pretty: Bool = true) -> String? {
        let encoder = JSONEncoder()
        if pretty {
            encoder.outputFormatting = [.prettyPrinted, .sortedKeys]
        }
        guard let data = try? encoder.encode(settings) else { return nil }
        return String(data: data, encoding: .utf8)
    }

    @discardableResult
    func importJSONString(_ payload: String) throws -> UserSettings {
        let data = Data(payload.utf8)
        var decoded = try JSONDecoder().decode(UserSettings.self, from: data)
        decoded.language = decoded.language.trimmingCharacters(in: .whitespacesAndNewlines)
        decoded.manifestURL = decoded.manifestURL.trimmingCharacters(in: .whitespacesAndNewlines)
        decoded.customPacksJSON = decoded.customPacksJSON.trimmingCharacters(in: .whitespacesAndNewlines)
        settings = decoded
        persist(decoded)
        return decoded
    }

    private func persist(_ value: UserSettings) {
        if let data = try? JSONEncoder().encode(value) {
            defaults.set(data, forKey: key)
        }
    }
}
