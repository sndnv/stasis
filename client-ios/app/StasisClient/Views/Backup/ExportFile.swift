import Foundation

enum ExportFile {
    static func write(name: String, fileExtension: String, bytes: Data) throws -> URL {
        let directory = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString)
        try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
        let safe = sanitize(name)
        let filename = fileExtension.isEmpty ? safe : "\(safe).\(fileExtension)"
        let url = directory.appendingPathComponent(filename)
        try bytes.write(to: url, options: .atomic)
        return url
    }

    static func inferExtension(_ bytes: Data) -> String {
        let prefix = [UInt8](bytes.prefix(12))
        if prefix.starts(with: [0x89, 0x50, 0x4E, 0x47]) { return "png" }
        if prefix.starts(with: [0xFF, 0xD8, 0xFF]) { return "jpg" }
        if prefix.starts(with: [0x47, 0x49, 0x46, 0x38]) { return "gif" }
        if prefix.starts(with: [0x25, 0x50, 0x44, 0x46]) { return "pdf" }
        if prefix.count >= 12, Array(prefix[4..<8]) == Array("ftyp".utf8) { return "heic" }
        if String(data: bytes, encoding: .utf8) != nil { return "txt" }
        return "bin"
    }

    static func sanitize(_ name: String) -> String {
        let trimmed = name.trimmingCharacters(in: .whitespacesAndNewlines)
        let cleaned = trimmed.map { character -> Character in
            character.isLetter || character.isNumber || character == "-" || character == "_" ? character : "_"
        }
        let result = String(cleaned)
        return result.isEmpty ? "content" : result
    }
}
