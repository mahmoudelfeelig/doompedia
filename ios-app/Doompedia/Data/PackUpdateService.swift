import CryptoKit
import Foundation

enum PackUpdateStatus {
    case noManifest
    case skippedNetwork
    case upToDate
    case updated
    case failed
}

struct PackUpdateResult {
    let status: PackUpdateStatus
    let installedVersion: Int
    let message: String
}

final class PackUpdateService {
    private let installer: PackInstaller
    private let deltaApplier: DeltaApplier
    private let downloader: ShardDownloader
    private let decoder = JSONDecoder()

    init(installer: PackInstaller, deltaApplier: DeltaApplier, downloader: ShardDownloader) {
        self.installer = installer
        self.deltaApplier = deltaApplier
        self.downloader = downloader
    }

    func checkAndApply(
        manifestURLString: String,
        wifiOnly: Bool,
        installedVersion: Int
    ) async -> PackUpdateResult {
        if manifestURLString.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
            return PackUpdateResult(status: .noManifest, installedVersion: installedVersion, message: "Manifest URL is empty")
        }

        do {
            guard let manifestURL = URL(string: manifestURLString) else {
                return PackUpdateResult(status: .failed, installedVersion: installedVersion, message: "Invalid manifest URL")
            }

            if wifiOnly && !NetworkMonitor.shared.isOnline {
                return PackUpdateResult(
                    status: .skippedNetwork,
                    installedVersion: installedVersion,
                    message: "Wi-Fi only mode is enabled"
                )
            }

            let manifest = try await fetchManifest(url: manifestURL)
            if manifest.version <= installedVersion {
                return PackUpdateResult(status: .upToDate, installedVersion: installedVersion, message: "No new pack available")
            }

            if manifest.compression != "none" && manifest.compression != "gzip" {
                return PackUpdateResult(
                    status: .failed,
                    installedVersion: installedVersion,
                    message: "Unsupported pack compression: \(manifest.compression)"
                )
            }

            let updateRoot = try updateDirectory(packID: manifest.packId, version: manifest.version)
            let deltaApplied = try await tryApplyDelta(
                manifestURL: manifestURL,
                manifest: manifest,
                updateRoot: updateRoot,
                wifiOnly: wifiOnly,
                installedVersion: installedVersion
            )

            if !deltaApplied {
                try await applyFullPack(
                    manifestURL: manifestURL,
                    manifest: manifest,
                    updateRoot: updateRoot,
                    wifiOnly: wifiOnly
                )
            }

            return PackUpdateResult(
                status: .updated,
                installedVersion: manifest.version,
                message: "Updated to pack version \(manifest.version)"
            )
        } catch {
            return PackUpdateResult(
                status: .failed,
                installedVersion: installedVersion,
                message: error.localizedDescription
            )
        }
    }

    private func fetchManifest(url: URL) async throws -> PackManifest {
        let (data, response) = try await URLSession.shared.data(from: url)
        guard let http = response as? HTTPURLResponse,
              (200 ... 299).contains(http.statusCode) else {
            throw NSError(domain: "Doompedia", code: 71, userInfo: [NSLocalizedDescriptionKey: "Manifest request failed"])
        }

        return try decoder.decode(PackManifest.self, from: data)
    }

    private func tryApplyDelta(
        manifestURL: URL,
        manifest: PackManifest,
        updateRoot: URL,
        wifiOnly: Bool,
        installedVersion: Int
    ) async throws -> Bool {
        guard let delta = manifest.delta,
              delta.baseVersion == installedVersion else {
            return false
        }

        let remoteURL = resolveURL(base: manifestURL, path: delta.url)
        let localURL = updateRoot.appendingPathComponent(URL(fileURLWithPath: delta.url).lastPathComponent)
        try await downloader.download(sourceURL: remoteURL, destinationURL: localURL, wifiOnly: wifiOnly, resume: true)

        let digest = try sha256(url: localURL)
        guard digest.caseInsensitiveCompare(delta.sha256) == .orderedSame else {
            try? FileManager.default.removeItem(at: localURL)
            return false
        }

        _ = try deltaApplier.apply(from: localURL)
        return true
    }

    private func applyFullPack(
        manifestURL: URL,
        manifest: PackManifest,
        updateRoot: URL,
        wifiOnly: Bool
    ) async throws {
        var localShards: [PackShard] = []

        for shard in manifest.shards {
            let remoteURL = resolveURL(base: manifestURL, path: shard.url)
            let localName = URL(fileURLWithPath: shard.url).lastPathComponent
            let localURL = updateRoot.appendingPathComponent(localName)
            try await downloader.download(sourceURL: remoteURL, destinationURL: localURL, wifiOnly: wifiOnly, resume: true)

            let digest = try sha256(url: localURL)
            guard digest.caseInsensitiveCompare(shard.sha256) == .orderedSame else {
                throw NSError(
                    domain: "Doompedia",
                    code: 72,
                    userInfo: [NSLocalizedDescriptionKey: "Checksum mismatch for shard \(shard.id)"]
                )
            }

            localShards.append(
                PackShard(
                    id: shard.id,
                    url: localName,
                    sha256: shard.sha256,
                    records: shard.records,
                    bytes: Int64((try? localURL.resourceValues(forKeys: [.fileSizeKey]).fileSize) ?? 0)
                )
            )
        }

        let localManifest = PackManifest(
            packId: manifest.packId,
            language: manifest.language,
            version: manifest.version,
            createdAt: manifest.createdAt,
            recordCount: manifest.recordCount,
            compression: manifest.compression,
            shards: localShards,
            delta: manifest.delta,
            topicDistribution: manifest.topicDistribution,
            attribution: manifest.attribution
        )

        let localManifestURL = updateRoot.appendingPathComponent("manifest.json")
        let data = try JSONEncoder().encode(localManifest)
        try data.write(to: localManifestURL, options: .atomic)
        _ = try installer.install(from: updateRoot, expectedPackID: manifest.packId)
    }

    private func updateDirectory(packID: String, version: Int) throws -> URL {
        let base = try FileManager.default.url(
            for: .applicationSupportDirectory,
            in: .userDomainMask,
            appropriateFor: nil,
            create: true
        )
        let dir = base
            .appendingPathComponent("doompedia")
            .appendingPathComponent("updates")
            .appendingPathComponent(packID)
            .appendingPathComponent("v\(version)")
        try FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
        return dir
    }

    private func resolveURL(base: URL, path: String) -> URL {
        URL(string: path, relativeTo: base) ?? base.appendingPathComponent(path)
    }

    private func sha256(url: URL) throws -> String {
        let data = try Data(contentsOf: url)
        let digest = SHA256.hash(data: data)
        return digest.map { String(format: "%02x", $0) }.joined()
    }
}
