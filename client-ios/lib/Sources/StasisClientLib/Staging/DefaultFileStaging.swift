import Foundation

public struct DefaultFileStaging: FileStaging {
    private let storeDirectory: URL?
    private let prefix: String
    private let suffix: String

    public init(storeDirectory: URL?, prefix: String, suffix: String) {
        self.storeDirectory = storeDirectory
        self.prefix = prefix
        self.suffix = suffix
    }

    public func temporary() async throws -> URL {
        let directory = storeDirectory ?? FileManager.default.temporaryDirectory
        let url = directory.appendingPathComponent("\(prefix)\(UUID().uuidString)\(suffix)")
        let attributes: [FileAttributeKey: Any] = [.posixPermissions: NSNumber(value: 0o600)]
        guard FileManager.default.createFile(atPath: url.path, contents: nil, attributes: attributes) else {
            throw FileStagingError.temporaryFileCreationFailed(path: url.path)
        }
        return url
    }

    public func discard(file: URL) async throws {
        do {
            try FileManager.default.removeItem(at: file)
        } catch let error as NSError where error.domain == NSCocoaErrorDomain
                                       && error.code == NSFileNoSuchFileError {
            return
        }
    }

    public func destage(from source: URL, to target: URL) async throws {
        let fileManager = FileManager.default
        if fileManager.fileExists(atPath: target.path) {
            _ = try fileManager.replaceItemAt(target, withItemAt: source)
        } else {
            try fileManager.moveItem(at: source, to: target)
        }
    }
}

public enum FileStagingError: Error, Equatable, LocalizedError {
    case temporaryFileCreationFailed(path: String)

    public var errorDescription: String? {
        switch self {
        case .temporaryFileCreationFailed(let path):
            "Failed to create a temporary file at [\(path)]"
        }
    }
}
