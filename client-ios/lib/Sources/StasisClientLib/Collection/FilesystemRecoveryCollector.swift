import Foundation

public struct FilesystemRecoveryCollector: RecoveryCollector {
    public typealias KeepDecision = @Sendable (String, FilesystemMetadata.EntityState) -> Bool

    private let targetMetadata: DatasetMetadata
    private let keep: KeepDecision
    private let destination: TargetEntity.Destination
    private let metadataCollector: any RecoveryMetadataCollector
    private let clients: any Clients

    public init(
        targetMetadata: DatasetMetadata,
        keep: @escaping KeepDecision,
        destination: TargetEntity.Destination,
        metadataCollector: any RecoveryMetadataCollector,
        clients: any Clients
    ) {
        self.targetMetadata = targetMetadata
        self.keep = keep
        self.destination = destination
        self.metadataCollector = metadataCollector
        self.clients = clients
    }

    public func collect() -> AsyncThrowingStream<TargetEntity, Error> {
        AsyncThrowingStream { continuation in
            let task = Task {
                do {
                    let entityMetadata = try await FilesystemRecoveryCollector.collectEntityMetadata(
                        targetMetadata: targetMetadata,
                        keep: keep,
                        clients: clients
                    )
                    for metadata in entityMetadata {
                        let target = try await metadataCollector.collect(
                            entity: URL(fileURLWithPath: metadata.path),
                            destination: destination,
                            existingMetadata: metadata
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

    public static func collectEntityMetadata(
        targetMetadata: DatasetMetadata,
        keep: KeepDecision,
        clients: any Clients
    ) async throws -> [EntityMetadata] {
        let kept = targetMetadata.filesystem.collect { entity, state in
            keep(entity, state) ? entity : nil
        }
        var collected: [EntityMetadata] = []
        for entity in kept {
            collected.append(try await targetMetadata.require(entity: entity, clients: clients))
        }
        return collected
    }
}
