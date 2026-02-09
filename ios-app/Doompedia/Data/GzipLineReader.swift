import Foundation
import zlib

enum GzipLineReader {
    static func forEachLine(at fileURL: URL, _ body: (String) throws -> Void) throws {
        guard let handle = gzopen(fileURL.path, "rb") else {
            throw NSError(
                domain: "Doompedia",
                code: 81,
                userInfo: [NSLocalizedDescriptionKey: "Unable to open gzip file: \(fileURL.lastPathComponent)"]
            )
        }
        defer { gzclose(handle) }

        var chunk = [UInt8](repeating: 0, count: 64 * 1024)
        var buffer = Data()
        let newlineByte = UInt8(ascii: "\n")

        while true {
            let readCount = gzread(handle, &chunk, UInt32(chunk.count))
            if readCount < 0 {
                throw NSError(
                    domain: "Doompedia",
                    code: 82,
                    userInfo: [NSLocalizedDescriptionKey: "Failed reading gzip stream: \(fileURL.lastPathComponent)"]
                )
            }
            if readCount == 0 {
                break
            }

            buffer.append(contentsOf: chunk.prefix(Int(readCount)))

            while let newlineIndex = buffer.firstIndex(of: newlineByte) {
                let lineData = buffer.prefix(upTo: newlineIndex)
                if let line = String(data: lineData, encoding: .utf8),
                   !line.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
                    try body(line)
                }
                let removeThrough = buffer.index(after: newlineIndex)
                buffer.removeSubrange(..<removeThrough)
            }
        }

        if !buffer.isEmpty,
           let line = String(data: buffer, encoding: .utf8),
           !line.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
            try body(line)
        }
    }
}
