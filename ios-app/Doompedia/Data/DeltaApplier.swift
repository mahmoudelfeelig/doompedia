import Foundation

final class DeltaApplier {
    private let store: SQLiteStore
    private let decoder = JSONDecoder()

    init(store: SQLiteStore) {
        self.store = store
    }

    func apply(from fileURL: URL) throws -> Int {
        if fileURL.pathExtension == "zst" {
            throw NSError(
                domain: "Doompedia",
                code: 51,
                userInfo: [NSLocalizedDescriptionKey: "Compressed delta apply requires decoder integration"]
            )
        }

        var upserts: [SeedRow] = []
        var deletes: [Int64] = []
        var applied = 0

        let lineReader: ((URL, (String) throws -> Void) throws -> Void) = fileURL.pathExtension == "gz"
            ? GzipLineReader.forEachLine
            : LineReader.forEachLine

        try lineReader(fileURL) { line in
            let row = try decoder.decode(DeltaRowPayload.self, from: Data(line.utf8))
            switch row.op {
            case "upsert":
                guard let record = row.record else { return }
                upserts.append(
                    SeedRow(
                        page_id: record.page_id,
                        lang: record.lang,
                        title: record.title,
                        summary: record.summary,
                        wiki_url: record.wiki_url,
                        topic_key: record.topic_key,
                        quality_score: record.quality_score,
                        is_disambiguation: record.is_disambiguation,
                        source_rev_id: record.source_rev_id,
                        updated_at: record.updated_at,
                        aliases: row.aliases ?? []
                    )
                )
                applied += 1

            case "delete":
                if let pageID = row.page_id {
                    deletes.append(pageID)
                    applied += 1
                }

            default:
                throw NSError(
                    domain: "Doompedia",
                    code: 52,
                    userInfo: [NSLocalizedDescriptionKey: "Unknown delta op: \(row.op)"]
                )
            }

            if upserts.count + deletes.count >= 1000 {
                try flush(upserts: &upserts, deletes: &deletes)
            }
        }

        try flush(upserts: &upserts, deletes: &deletes)
        return applied
    }

    private func flush(upserts: inout [SeedRow], deletes: inout [Int64]) throws {
        if !upserts.isEmpty {
            try store.upsertSeedRows(upserts)
            upserts.removeAll(keepingCapacity: true)
        }

        if !deletes.isEmpty {
            try store.deleteArticles(pageIDs: deletes)
            deletes.removeAll(keepingCapacity: true)
        }
    }
}
