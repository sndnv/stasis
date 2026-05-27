import Foundation

public struct TargetEntity: Sendable, Equatable, Hashable {
    public let path: URL
    public let destination: Destination
    public let existingMetadata: EntityMetadata
    public let currentMetadata: EntityMetadata?

    public init(
        path: URL,
        destination: Destination,
        existingMetadata: EntityMetadata,
        currentMetadata: EntityMetadata?
    ) throws {
        if let current = currentMetadata, !existingMetadata.sameKind(as: current) {
            throw EntityMetadataMismatch(
                currentPath: current.path,
                existingPath: existingMetadata.path
            )
        }
        self.path = path
        self.destination = destination
        self.existingMetadata = existingMetadata
        self.currentMetadata = currentMetadata
    }

    public var originalPath: URL {
        URL(fileURLWithPath: existingMetadata.path)
    }

    public var destinationPath: URL {
        switch destination {
        case .default:
            originalPath
        case .directory(let path, let keepDefaultStructure):
            if keepDefaultStructure {
                path.appendingPathComponent(originalPath.path)
            } else {
                path.appendingPathComponent(originalPath.lastPathComponent)
            }
        }
    }

    public var hasChanged: Bool {
        guard let current = currentMetadata else { return true }
        return existingMetadata.hasChanged(comparedTo: current)
    }

    public var hasContentChanged: Bool {
        switch existingMetadata {
        case .file(let existing):
            if case .file(let current) = currentMetadata {
                existing.size != current.size || existing.checksum != current.checksum
            } else {
                true
            }
        case .directory:
            false
        }
    }

    public enum Destination: Sendable, Equatable, Hashable {
        case `default`
        case directory(path: URL, keepDefaultStructure: Bool)
    }
}
