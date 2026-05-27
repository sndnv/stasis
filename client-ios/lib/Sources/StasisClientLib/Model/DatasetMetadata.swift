import Foundation

public struct DatasetMetadata: Sendable, Equatable, Hashable {
    public let contentChanged: [String: EntityMetadata]
    public let metadataChanged: [String: EntityMetadata]
    public let filesystem: FilesystemMetadata

    public init(
        contentChanged: [String: EntityMetadata],
        metadataChanged: [String: EntityMetadata],
        filesystem: FilesystemMetadata
    ) {
        self.contentChanged = contentChanged
        self.metadataChanged = metadataChanged
        self.filesystem = filesystem
    }

    public var contentChangedBytes: Int64 {
        contentChanged.values.reduce(0) { acc, metadata in
            if case .file(let file) = metadata {
                acc + file.size
            } else {
                acc
            }
        }
    }
}
