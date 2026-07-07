import Foundation

extension Recovery {
    public struct EntityCollection: Sendable {
        public let targetMetadata: DatasetMetadata
        public let keep: @Sendable (String, FilesystemMetadata.EntityState) -> Bool
        public let destination: TargetEntity.Destination
        public let providers: RecoveryProviders

        public init(
            targetMetadata: DatasetMetadata,
            keep: @escaping @Sendable (String, FilesystemMetadata.EntityState) -> Bool,
            destination: TargetEntity.Destination,
            providers: RecoveryProviders
        ) {
            self.targetMetadata = targetMetadata
            self.keep = keep
            self.destination = destination
            self.providers = providers
        }

        public func collect(operation: OperationId) -> AsyncThrowingStream<TargetEntity, Error> {
            AsyncThrowingStream { continuation in
                let task = Task {
                    do {
                        for kind in providers.kinds {
                            let collector = kind.collector(
                                targetMetadata: targetMetadata,
                                keep: keep,
                                destination: destination,
                                providers: providers
                            )
                            for try await entity in collector.collect() {
                                await providers.track.entityExamined(
                                    operation: operation,
                                    entity: entity.ref,
                                    metadataChanged: entity.hasChanged,
                                    contentChanged: entity.hasContentChanged
                                )
                                if entity.hasChanged {
                                    await providers.track.entityCollected(operation: operation, entity: entity)
                                    continuation.yield(entity)
                                }
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
}
