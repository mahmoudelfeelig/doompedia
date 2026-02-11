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

struct PackUpdateProgress {
    let phase: String
    let percent: Double
    let downloadedBytes: Int64
    let totalBytes: Int64
    let bytesPerSecond: Int64
    let detail: String
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
        installedVersion: Int,
        onProgress: ((PackUpdateProgress) -> Void)? = nil
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

            onProgress?(PackUpdateProgress(
                phase: "Fetching manifest",
                percent: 0,
                downloadedBytes: 0,
                totalBytes: 0,
                bytesPerSecond: 0,
                detail: manifestURLString
            ))
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
                installedVersion: installedVersion,
                onProgress: onProgress
            )

            if !deltaApplied {
                try await applyFullPack(
                    manifestURL: manifestURL,
                    manifest: manifest,
                    updateRoot: updateRoot,
                    wifiOnly: wifiOnly,
                    onProgress: onProgress
                )
            }

            onProgress?(PackUpdateProgress(
                phase: "Completed",
                percent: 100,
                downloadedBytes: manifest.shards.reduce(0) { $0 + $1.bytes },
                totalBytes: manifest.shards.reduce(0) { $0 + $1.bytes },
                bytesPerSecond: 0,
                detail: "Installed pack v\(manifest.version)"
            ))
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
        installedVersion: Int,
        onProgress: ((PackUpdateProgress) -> Void)? = nil
    ) async throws -> Bool {
        guard let delta = manifest.delta,
              delta.baseVersion == installedVersion else {
            return false
        }

        let remoteURL = resolveURL(base: manifestURL, path: delta.url)
        let localURL = updateRoot.appendingPathComponent(URL(fileURLWithPath: delta.url).lastPathComponent)
        onProgress?(PackUpdateProgress(
            phase: "Downloading delta",
            percent: 0,
            downloadedBytes: 0,
            totalBytes: 0,
            bytesPerSecond: 0,
            detail: localURL.lastPathComponent
        ))
        try await downloader.download(
            sourceURL: remoteURL,
            destinationURL: localURL,
            wifiOnly: wifiOnly,
            resume: true
        ) { downloaded, total, speed in
            let percent = total > 0 ? (Double(downloaded) / Double(total)) * 100.0 : 0
            onProgress?(PackUpdateProgress(
                phase: "Downloading delta",
                percent: max(0, min(100, percent)),
                downloadedBytes: downloaded,
                totalBytes: total,
                bytesPerSecond: speed,
                detail: localURL.lastPathComponent
            ))
        }

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
        wifiOnly: Bool,
        onProgress: ((PackUpdateProgress) -> Void)? = nil
    ) async throws {
        var localShards: [PackShard] = []
        let totalBytes = max(1, manifest.shards.reduce(Int64(0)) { $0 + $1.bytes })
        var completedBytes: Int64 = 0

        for shard in manifest.shards {
            let remoteURL = resolveURL(base: manifestURL, path: shard.url)
            let localName = URL(fileURLWithPath: shard.url).lastPathComponent
            let localURL = updateRoot.appendingPathComponent(localName)
            try await downloader.download(
                sourceURL: remoteURL,
                destinationURL: localURL,
                wifiOnly: wifiOnly,
                resume: true
            ) { downloaded, _, speed in
                let global = min(totalBytes, completedBytes + downloaded)
                let percent = (Double(global) / Double(totalBytes)) * 100.0
                onProgress?(PackUpdateProgress(
                    phase: "Downloading shards",
                    percent: max(0, min(100, percent)),
                    downloadedBytes: global,
                    totalBytes: totalBytes,
                    bytesPerSecond: speed,
                    detail: shard.id
                ))
            }

            let digest = try sha256(url: localURL)
            guard digest.caseInsensitiveCompare(shard.sha256) == .orderedSame else {
                throw NSError(
                    domain: "Doompedia",
                    code: 72,
                    userInfo: [NSLocalizedDescriptionKey: "Checksum mismatch for shard \(shard.id)"]
                )
            }
            completedBytes += min(shard.bytes, Int64((try? localURL.resourceValues(forKeys: [.fileSizeKey]).fileSize) ?? 0))

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
            description: manifest.description,
            packTags: manifest.packTags,
            compression: manifest.compression,
            shards: localShards,
            delta: manifest.delta,
            topicDistribution: manifest.topicDistribution,
            entityDistribution: manifest.entityDistribution,
            sampleKeywords: manifest.sampleKeywords,
            attribution: manifest.attribution
        )

        let localManifestURL = updateRoot.appendingPathComponent("manifest.json")
        let data = try JSONEncoder().encode(localManifest)
        try data.write(to: localManifestURL, options: .atomic)
        onProgress?(PackUpdateProgress(
            phase: "Installing",
            percent: 100,
            downloadedBytes: totalBytes,
            totalBytes: totalBytes,
            bytesPerSecond: 0,
            detail: "Applying downloaded content"
        ))
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
