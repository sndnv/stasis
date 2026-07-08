import Foundation

struct DropInbox: Sendable {
    static let metadataFileName = "metadata.json"

    let directory: URL

    static var `default`: DropInbox? {
        guard let container = AppGroup.containerURL() else { return nil }
        return DropInbox(directory: container.appendingPathComponent("Drops", isDirectory: true))
    }

    @discardableResult
    func store(
        filename: String,
        typeIdentifier: String?,
        writeContent: (URL) async throws -> Void
    ) async throws -> DropMetadata {
        let id = UUID().uuidString
        let folder = folderURL(id: id)
        try FileManager.default.createDirectory(at: folder, withIntermediateDirectories: true)
        let safeName = Self.sanitize(filename)
        let contentURL = folder.appendingPathComponent(safeName)
        try await writeContent(contentURL)
        let metadata = DropMetadata(
            id: id,
            filename: safeName,
            size: Self.fileSize(contentURL),
            typeIdentifier: typeIdentifier,
            createdAt: Date()
        )
        try metadata.encoded().write(to: metadataURL(id: id), options: .atomic)
        return metadata
    }

    @discardableResult
    func store(filename: String, typeIdentifier: String?, from source: URL) async throws -> DropMetadata {
        try await store(filename: filename, typeIdentifier: typeIdentifier) { contentURL in
            try FileManager.default.copyItem(at: source, to: contentURL)
        }
    }

    @discardableResult
    func store(filename: String, typeIdentifier: String?, data: Data) async throws -> DropMetadata {
        try await store(filename: filename, typeIdentifier: typeIdentifier) { contentURL in
            try data.write(to: contentURL, options: .atomic)
        }
    }

    func list() -> [DropMetadata] {
        let folders = (try? FileManager.default.contentsOfDirectory(
            at: directory,
            includingPropertiesForKeys: nil
        )) ?? []
        return folders
            .compactMap { folder in
                (try? Data(contentsOf: folder.appendingPathComponent(Self.metadataFileName)))
                    .flatMap(DropMetadata.decoded(from:))
            }
            .sorted { $0.createdAt < $1.createdAt }
    }

    func contentURL(for metadata: DropMetadata) -> URL {
        folderURL(id: metadata.id).appendingPathComponent(metadata.filename)
    }

    func contentURL(forPath path: String) -> URL {
        let (id, filename) = Self.split(path: path)
        return folderURL(id: id).appendingPathComponent(filename)
    }

    func metadataURL(id: String) -> URL {
        folderURL(id: id).appendingPathComponent(Self.metadataFileName)
    }

    func restore(_ metadata: DropMetadata, content: AsyncThrowingStream<Data, Error>) async throws {
        let folder = folderURL(id: metadata.id)
        try FileManager.default.createDirectory(at: folder, withIntermediateDirectories: true)
        let contentURL = folder.appendingPathComponent(metadata.filename)
        if !FileManager.default.fileExists(atPath: contentURL.path) {
            FileManager.default.createFile(atPath: contentURL.path, contents: nil)
        }
        let handle = try FileHandle(forWritingTo: contentURL)
        do {
            for try await chunk in content { try handle.write(contentsOf: chunk) }
            try handle.close()
        } catch {
            try? handle.close()
            throw error
        }
        try metadata.encoded().write(to: metadataURL(id: metadata.id), options: .atomic)
    }

    func remove(id: String) throws {
        try FileManager.default.removeItem(at: folderURL(id: id))
    }

    private func folderURL(id: String) -> URL {
        directory.appendingPathComponent(id, isDirectory: true)
    }

    static func split(path: String) -> (id: String, filename: String) {
        let trimmed = path.hasPrefix("/") ? String(path.dropFirst()) : path
        let parts = trimmed.split(separator: "/", maxSplits: 1, omittingEmptySubsequences: false).map(String.init)
        return (parts.first ?? "", parts.count > 1 ? parts[1] : "")
    }

    private static func sanitize(_ name: String) -> String {
        let base = (name as NSString).lastPathComponent
        let cleaned = base.map { character -> Character in
            character == "/" || character == ":" ? "_" : character
        }
        let result = String(cleaned).trimmingCharacters(in: .whitespacesAndNewlines)
        return result.isEmpty ? "content" : result
    }

    private static func fileSize(_ url: URL) -> Int64 {
        let values = try? url.resourceValues(forKeys: [.fileSizeKey])
        return Int64(values?.fileSize ?? 0)
    }
}
