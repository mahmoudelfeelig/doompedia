import CryptoKit
import Foundation

final class PackInstaller {
    private let store: SQLiteStore
    private let decoder = JSONDecoder()

    init(store: SQLiteStore) {
        self.store = store
    }

    func install(from directory: URL, expectedPackID: String? = nil) throws -> PackManifest {
        let manifestURL = directory.appendingPathComponent("manifest.json")
        let manifestData = try Data(contentsOf: manifestURL)
        let manifest = try decoder.decode(PackManifest.self, from: manifestData)

        if let expectedPackID, manifest.packId != expectedPackID {
            throw NSError(domain: "Doompedia", code: 41, userInfo: [NSLocalizedDescriptionKey: "Unexpected pack id \(manifest.packId)"])
        }

        for shard in manifest.shards {
            let shardURL = resolveShardURL(baseDirectory: directory, shardPath: shard.url)
            try validateChecksum(url: shardURL, expected: shard.sha256)
            try applyShard(fileURL: shardURL)
        }

        return manifest
    }

    private func applyShard(fileURL: URL) throws {
        if fileURL.pathExtension == "zst" {
            throw NSError(
                domain: "Doompedia",
                code: 42,
                userInfo: [NSLocalizedDescriptionKey: "Compressed shard install requires decoder integration"]
            )
        }

        var batch: [SeedRow] = []

        let lineReader: ((URL, (String) throws -> Void) throws -> Void) = fileURL.pathExtension == "gz"
            ? GzipLineReader.forEachLine
            : LineReader.forEachLine

        try lineReader(fileURL) { line in
            let payload = try decoder.decode(ShardRowPayload.self, from: Data(line.utf8))
            let record = payload.article
            batch.append(
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
                    aliases: payload.aliases
                )
            )

            if batch.count >= 1000 {
                try store.upsertSeedRows(batch)
                batch.removeAll(keepingCapacity: true)
            }
        }

        if !batch.isEmpty {
            try store.upsertSeedRows(batch)
        }
    }

    private func validateChecksum(url: URL, expected: String) throws {
        let data = try Data(contentsOf: url)
        let digest = SHA256.hash(data: data)
        let hash = digest.map { String(format: "%02x", $0) }.joined()
        guard hash.lowercased() == expected.lowercased() else {
            throw NSError(domain: "Doompedia", code: 43, userInfo: [NSLocalizedDescriptionKey: "Checksum mismatch for \(url.lastPathComponent)"])
        }
    }

    private func resolveShardURL(baseDirectory: URL, shardPath: String) -> URL {
        let direct = baseDirectory.appendingPathComponent(shardPath)
        if FileManager.default.fileExists(atPath: direct.path) {
            return direct
        }

        return baseDirectory.appendingPathComponent(URL(fileURLWithPath: shardPath).lastPathComponent)
    }
}
