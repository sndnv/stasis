import Foundation

public struct SourceEntity: Sendable, Equatable, Hashable {
    public let ref: EntityRef
    public let existingMetadata: EntityMetadata?
    public let currentMetadata: EntityMetadata

    public init(ref: EntityRef, existingMetadata: EntityMetadata?, currentMetadata: EntityMetadata) throws {
        if let existing = existingMetadata, !existing.sameKind(as: currentMetadata) {
            throw EntityMetadataMismatch(
                currentPath: currentMetadata.path,
                existingPath: existing.path
            )
        }
        self.ref = ref
        self.existingMetadata = existingMetadata
        self.currentMetadata = currentMetadata
    }

    public var hasChanged: Bool {
        guard let existing = existingMetadata else { return true }
        return existing.hasChanged(comparedTo: currentMetadata)
    }

    public var hasContentChanged: Bool {
        if let existingContent = existingMetadata?.content, let currentContent = currentMetadata.content {
            return existingContent.size != currentContent.size || existingContent.checksum != currentContent.checksum
        }
        if existingMetadata == nil, currentMetadata.content != nil {
            return true
        }
        return false
    }
}
