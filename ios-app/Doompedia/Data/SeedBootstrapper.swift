import Foundation

enum SeedBootstrapper {
    static func ensureSeedData(store: SQLiteStore) throws {
        if try store.articleCount() > 0 {
            return
        }

        guard let url = Bundle.main.url(forResource: "seed_en_cards", withExtension: "json") else {
            throw NSError(domain: "Doompedia", code: 2, userInfo: [NSLocalizedDescriptionKey: "Missing seed data asset"])
        }

        let data = try Data(contentsOf: url)
        let rows = try JSONDecoder().decode([SeedRow].self, from: data)
        try store.upsertSeedRows(rows)
    }
}
