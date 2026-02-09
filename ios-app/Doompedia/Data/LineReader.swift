import Foundation

enum LineReader {
    static func forEachLine(at fileURL: URL, _ body: (String) throws -> Void) throws {
        let handle = try FileHandle(forReadingFrom: fileURL)
        defer {
            try? handle.close()
        }

        var buffer = Data()
        let newlineByte = UInt8(ascii: "\n")

        while true {
            let chunk = try handle.read(upToCount: 64 * 1024) ?? Data()
            if chunk.isEmpty {
                break
            }

            buffer.append(chunk)

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
