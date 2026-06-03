import Foundation

public struct DefaultBackupCollector: BackupCollector {
    private let entities: [URL]
    private let latestMetadata: DatasetMetadata?
    private let metadataCollector: any BackupMetadataCollector
    private let clients: any Clients

    public init(
        entities: [URL],
        latestMetadata: DatasetMetadata?,
        metadataCollector: any BackupMetadataCollector,
        clients: any Clients
    ) {
        self.entities = entities
        self.latestMetadata = latestMetadata
        self.metadataCollector = metadataCollector
        self.clients = clients
    }

    public func collect() -> AsyncThrowingStream<SourceEntity, Error> {
        AsyncThrowingStream { continuation in
            let task = Task {
                do {
                    let stream = DefaultBackupCollector.collectEntityMetadata(
                        entities: entities,
                        latestMetadata: latestMetadata,
                        clients: clients
                    )
                    for try await (entity, existing) in stream {
                        let source = try await metadataCollector.collect(entity: entity, existingMetadata: existing)
                        continuation.yield(source)
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
        entities: [URL],
        latestMetadata: DatasetMetadata?,
        clients: any Clients
    ) -> AsyncThrowingStream<(URL, EntityMetadata?), Error> {
        AsyncThrowingStream { continuation in
            let task = Task {
                do {
                    for entity in entities {
                        if let latestMetadata {
                            let metadata = try await latestMetadata.collect(entity: entity, clients: clients)
                            continuation.yield((entity, metadata))
                        } else {
                            continuation.yield((entity, nil))
                        }
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
