import Foundation

public protocol BackupEntityKind: Sendable {
    func collector(
        operation: OperationId,
        collector: Backup.EntityDiscovery.Collector,
        latestMetadata: DatasetMetadata?,
        providers: BackupProviders
    ) async throws -> any BackupCollector
}

public protocol BackupFilesystemKind: BackupEntityKind {
    func read(entity: SourceEntity, ref: URL, chunkSize: Int) -> AsyncThrowingStream<Data, Error>
}

public protocol BackupLibraryKind: BackupEntityKind {
    var scheme: String { get }

    func read(entity: SourceEntity, scheme: String, path: String, chunkSize: Int) -> AsyncThrowingStream<Data, Error>
}

public enum BackupEntityKinds {
    public static let filesystem: any BackupFilesystemKind = Filesystem()

    public static func read(
        kinds: [any BackupEntityKind],
        entity: SourceEntity,
        chunkSize: Int
    ) throws -> AsyncThrowingStream<Data, Error> {
        switch entity.ref {
        case .filesystem(let url):
            guard let kind = kinds.compactMap({ $0 as? any BackupFilesystemKind }).first else {
                throw InvalidArgumentError("No filesystem backup kind was registered")
            }
            return kind.read(entity: entity, ref: url, chunkSize: chunkSize)
        case .library(let scheme, let path):
            guard let kind = kinds.compactMap({ $0 as? any BackupLibraryKind }).first(where: { $0.scheme == scheme }) else {
                throw InvalidArgumentError("No backup kind was registered for scheme [\(scheme)]")
            }
            return kind.read(entity: entity, scheme: scheme, path: path, chunkSize: chunkSize)
        }
    }

    private struct Filesystem: BackupFilesystemKind {
        func collector(
            operation: OperationId,
            collector: Backup.EntityDiscovery.Collector,
            latestMetadata: DatasetMetadata?,
            providers: BackupProviders
        ) async throws -> any BackupCollector {
            let entities = await resolveEntities(operation: operation, collector: collector, providers: providers)
            return FilesystemBackupCollector(
                entities: entities,
                latestMetadata: latestMetadata,
                metadataCollector: FilesystemBackupMetadataCollector(
                    checksum: providers.checksum,
                    compression: providers.compression
                ),
                clients: providers.clients
            )
        }

        func read(entity: SourceEntity, ref: URL, chunkSize: Int) -> AsyncThrowingStream<Data, Error> {
            FileByteSource.read(ref, chunkSize: chunkSize)
        }

        private func resolveEntities(
            operation: OperationId,
            collector: Backup.EntityDiscovery.Collector,
            providers: BackupProviders
        ) async -> [EntityRef] {
            switch collector {
            case .withRules(let rules):
                let spec = Specification.build(rules: rules.filter { SourceUri.scheme($0.source) == nil }) { _ in }
                for url in spec.included {
                    await providers.track.entityDiscovered(operation: operation, entity: .filesystem(url))
                }
                await providers.track.specificationProcessed(operation: operation, unmatched: spec.unmatched)
                return spec.included.map { EntityRef.filesystem($0) }
            case .withEntities(let provided):
                let existing = provided.filter { FileManager.default.fileExists(atPath: $0.path) }
                for entity in existing {
                    await providers.track.entityDiscovered(operation: operation, entity: .filesystem(entity))
                }
                return existing.map { EntityRef.filesystem($0) }
            case .withState(let state):
                return state.remainingEntities().filter { ref in
                    if case .filesystem = ref { return true } else { return false }
                }
            }
        }
    }
}
