import Foundation
import StasisClientLib

final class DropEntityKind: BackupLibraryKind, RecoveryLibraryKind, Sendable {
    let scheme = "drop"

    private let inbox: DropInbox

    init(inbox: DropInbox) {
        self.inbox = inbox
    }

    func collector(
        operation: OperationId,
        collector: Backup.EntityDiscovery.Collector,
        latestMetadata: DatasetMetadata?,
        providers: BackupProviders
    ) async throws -> any BackupCollector {
        Collector(
            kind: self,
            operation: operation,
            discovery: collector,
            latestMetadata: latestMetadata,
            providers: providers
        )
    }

    func read(
        entity: SourceEntity,
        scheme: String,
        path: String,
        chunkSize: Int
    ) -> AsyncThrowingStream<Data, Error> {
        FileByteSource.read(inbox.contentURL(forPath: path), chunkSize: chunkSize)
    }

    func collector(
        targetMetadata: DatasetMetadata,
        keep: @escaping @Sendable (String, FilesystemMetadata.EntityState) -> Bool,
        destination: TargetEntity.Destination,
        providers: RecoveryProviders
    ) -> any RecoveryCollector {
        RecoveryCollectorImpl(
            scheme: scheme,
            targetMetadata: targetMetadata,
            keep: keep,
            destination: destination,
            clients: providers.clients
        )
    }

    func prepare(entity: TargetEntity, scheme: String, path: String, providers: RecoveryProviders) throws {}

    func write(
        entity: TargetEntity,
        scheme: String,
        path: String,
        content: AsyncThrowingStream<Data, Error>,
        providers: RecoveryProviders
    ) async throws {
        guard case .library(let existing) = entity.existingMetadata else {
            throw InvalidArgumentError("Expected library metadata for [\(path)]")
        }
        let metadata = DropMetadata.decoded(from: existing.attributes)
            ?? Self.metadata(forPath: path, size: existing.size, createdAt: existing.created)
        try await inbox.restore(metadata, content: content)
    }

    func applyMetadata(entity: TargetEntity, scheme: String, path: String, providers: RecoveryProviders) async throws {}

    fileprivate func resolveDrops(_ collector: Backup.EntityDiscovery.Collector) -> [DropMetadata] {
        switch collector {
        case .withRules: inbox.list()
        case .withState(let state): dropsFromState(state)
        case .withEntities: []
        }
    }

    fileprivate func sourceEntity(
        drop: DropMetadata,
        latestMetadata: DatasetMetadata?,
        providers: BackupProviders
    ) async throws -> SourceEntity? {
        let ref = EntityRef.library(scheme: scheme, path: Self.path(for: drop))

        if try await latestMetadata?.collect(entity: ref.key, clients: providers.clients) != nil {
            try? inbox.remove(id: drop.id)
            return nil
        }

        let checksum = try await providers.checksum.calculate(file: inbox.contentURL(for: drop))
        let current = EntityMetadata.Library(
            path: ref.key,
            created: drop.createdAt,
            updated: drop.createdAt,
            size: drop.size,
            checksum: checksum,
            crates: [:],
            compression: providers.compression.algorithmFor(entity: URL(fileURLWithPath: drop.filename)),
            attributes: try drop.encoded()
        )
        return try SourceEntity(
            ref: ref,
            existingMetadata: nil,
            currentMetadata: .library(current)
        )
    }

    private func dropsFromState(_ state: BackupState) -> [DropMetadata] {
        let wanted = Set(state.remainingEntities().compactMap { ref -> String? in
            guard case .library(let entityScheme, let path) = ref, entityScheme == scheme else { return nil }
            return path
        })
        return inbox.list().filter { wanted.contains(Self.path(for: $0)) }
    }

    private static func path(for drop: DropMetadata) -> String {
        "/\(drop.id)/\(drop.filename)"
    }

    private static func metadata(forPath path: String, size: Int64, createdAt: Date) -> DropMetadata {
        let (id, filename) = DropInbox.split(path: path)
        return DropMetadata(
            id: id.isEmpty ? UUID().uuidString : id,
            filename: filename.isEmpty ? "content" : filename,
            size: size,
            typeIdentifier: nil,
            createdAt: createdAt
        )
    }

    private struct Collector: BackupCollector {
        let kind: DropEntityKind
        let operation: OperationId
        let discovery: Backup.EntityDiscovery.Collector
        let latestMetadata: DatasetMetadata?
        let providers: BackupProviders

        func collect() -> AsyncThrowingStream<SourceEntity, Error> {
            AsyncThrowingStream { continuation in
                let task = Task {
                    do {
                        for drop in kind.resolveDrops(discovery) {
                            try Task.checkCancellation()
                            guard let entity = try await kind.sourceEntity(
                                drop: drop,
                                latestMetadata: latestMetadata,
                                providers: providers
                            ) else { continue }
                            await providers.track.entityDiscovered(operation: operation, entity: entity.ref)
                            continuation.yield(entity)
                        }
                        continuation.finish()
                    } catch {
                        continuation.finish(throwing: error)
                    }
                }
                continuation.onTermination = { _ in task.cancel() }
            }
        }
    }

    private struct RecoveryCollectorImpl: RecoveryCollector {
        let scheme: String
        let targetMetadata: DatasetMetadata
        let keep: @Sendable (String, FilesystemMetadata.EntityState) -> Bool
        let destination: TargetEntity.Destination
        let clients: any Clients

        func collect() -> AsyncThrowingStream<TargetEntity, Error> {
            AsyncThrowingStream { continuation in
                let task = Task {
                    do {
                        let kept = targetMetadata.filesystem.collect { entity, state in
                            SourceUri.scheme(entity) == scheme && keep(entity, state) ? entity : nil
                        }
                        for entity in kept {
                            let existing = try await targetMetadata.require(entity: entity, clients: clients)
                            let target = try TargetEntity(
                                ref: EntityRef.default(key: entity),
                                destination: destination,
                                existingMetadata: existing,
                                currentMetadata: nil
                            )
                            continuation.yield(target)
                        }
                        continuation.finish()
                    } catch {
                        continuation.finish(throwing: error)
                    }
                }
                continuation.onTermination = { _ in task.cancel() }
            }
        }
    }
}
