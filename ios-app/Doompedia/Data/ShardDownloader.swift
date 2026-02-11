import Foundation

final class ShardDownloader {
    private let session: URLSession

    init() {
        let config = URLSessionConfiguration.default
        config.timeoutIntervalForRequest = 30
        config.timeoutIntervalForResource = 120
        self.session = URLSession(configuration: config)
    }

    func download(
        sourceURL: URL,
        destinationURL: URL,
        wifiOnly: Bool,
        resume: Bool = true,
        onProgress: ((Int64, Int64, Int64) -> Void)? = nil
    ) async throws {
        var request = URLRequest(url: sourceURL)
        request.httpMethod = "GET"
        request.allowsCellularAccess = !wifiOnly

        let fileManager = FileManager.default
        try fileManager.createDirectory(at: destinationURL.deletingLastPathComponent(), withIntermediateDirectories: true)

        var existingBytes: UInt64 = 0
        if resume,
           let attrs = try? fileManager.attributesOfItem(atPath: destinationURL.path),
           let size = attrs[.size] as? UInt64 {
            existingBytes = size
        }

        if existingBytes > 0 {
            request.setValue("bytes=\(existingBytes)-", forHTTPHeaderField: "Range")
        }

        let started = Date()
        onProgress?(Int64(existingBytes), -1, 0)
        let (data, response) = try await session.data(for: request)
        guard let http = response as? HTTPURLResponse,
              (200...299).contains(http.statusCode) else {
            throw NSError(domain: "Doompedia", code: 61, userInfo: [NSLocalizedDescriptionKey: "Failed downloading shard"])
        }

        let expectedFromResponse = Int64(http.expectedContentLength)
        let totalBytes: Int64 = {
            guard expectedFromResponse > 0 else { return -1 }
            if existingBytes > 0 && http.statusCode == 206 {
                return Int64(existingBytes) + expectedFromResponse
            }
            return expectedFromResponse
        }()

        if existingBytes > 0 && http.statusCode == 206 {
            if fileManager.fileExists(atPath: destinationURL.path) {
                let handle = try FileHandle(forWritingTo: destinationURL)
                try handle.seekToEnd()
                try handle.write(contentsOf: data)
                try handle.close()
            } else {
                try data.write(to: destinationURL, options: .atomic)
            }
        } else {
            try data.write(to: destinationURL, options: .atomic)
        }

        let downloaded = Int64((try? fileManager.attributesOfItem(atPath: destinationURL.path)[.size] as? UInt64) ?? UInt64(data.count))
        let elapsed = max(0.001, Date().timeIntervalSince(started))
        let speed = Int64(Double(downloaded) / elapsed)
        onProgress?(downloaded, totalBytes, speed)
    }
}
