import Foundation

public struct SourceEntity: Sendable, Equatable, Hashable {
    public let path: URL
    public let existingMetadata: EntityMetadata?
    public let currentMetadata: EntityMetadata

    public init(path: URL, existingMetadata: EntityMetadata?, currentMetadata: EntityMetadata) throws {
        if let existing = existingMetadata, !existing.sameKind(as: currentMetadata) {
            throw EntityMetadataMismatch(
                currentPath: currentMetadata.path,
                existingPath: existing.path
            )
        }
        self.path = path
        self.existingMetadata = existingMetadata
        self.currentMetadata = currentMetadata
    }

    public var hasChanged: Bool {
        guard let existing = existingMetadata else { return true }
        return existing.hasChanged(comparedTo: currentMetadata)
    }

    public var hasContentChanged: Bool {
        switch currentMetadata {
        case .file(let current):
            if case .file(let existing) = existingMetadata {
                existing.size != current.size || existing.checksum != current.checksum
            } else {
                true
            }
        case .directory:
            false
        }
    }
}
