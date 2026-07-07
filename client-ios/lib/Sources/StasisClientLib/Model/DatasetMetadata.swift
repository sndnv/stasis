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
            if let content = metadata.content {
                acc + content.size
            } else {
                acc
            }
        }
    }

    public static func empty() -> DatasetMetadata {
        DatasetMetadata(
            contentChanged: [:],
            metadataChanged: [:],
            filesystem: .empty()
        )
    }

    public func collect(entity: URL, clients: any Clients) async throws -> EntityMetadata? {
        try await collect(entity: entity.path, clients: clients)
    }

    public func collect(entity: String, clients: any Clients) async throws -> EntityMetadata? {
        guard let state = filesystem.get(entity) else { return nil }
        switch state {
        case .new, .updated:
            guard let metadata = contentChanged[entity] ?? metadataChanged[entity] else {
                throw DatasetMetadataCollectError.missingMetadataForEntity(entity: entity)
            }
            return metadata
        case .existing(let entry):
            let entryMetadata = try await clients.api().datasetMetadata(entry: entry)
            guard let metadata = entryMetadata.contentChanged[entity] ?? entryMetadata.metadataChanged[entity] else {
                throw DatasetMetadataCollectError.missingMetadataForEntityInEntry(entity: entity, entry: entry)
            }
            return metadata
        }
    }

    public func require(entity: String, clients: any Clients) async throws -> EntityMetadata {
        guard let metadata = try await collect(entity: entity, clients: clients) else {
            throw DatasetMetadataCollectError.requiredMetadataMissing(entity: entity)
        }
        return metadata
    }
}

public enum DatasetMetadataCollectError: Error, Equatable {
    case missingMetadataForEntity(entity: String)
    case missingMetadataForEntityInEntry(entity: String, entry: DatasetEntryId)
    case requiredMetadataMissing(entity: String)
}
