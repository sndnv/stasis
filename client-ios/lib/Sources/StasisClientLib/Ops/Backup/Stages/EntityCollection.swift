import Foundation

extension Backup {
    public struct EntityCollection: Sendable {
        public let targetDataset: DatasetDefinition
        public let providers: BackupProviders

        public init(targetDataset: DatasetDefinition, providers: BackupProviders) {
            self.targetDataset = targetDataset
            self.providers = providers
        }

        public func collect(
            operation: OperationId,
            collectors: AsyncThrowingStream<any BackupCollector, Error>
        ) -> AsyncThrowingStream<SourceEntity, Error> {
            AsyncThrowingStream { continuation in
                let task = Task {
                    do {
                        for try await collector in collectors {
                            for try await entity in collector.collect() {
                                await providers.track.entityExamined(operation: operation, entity: entity.ref)
                                if entity.hasChanged {
                                    await providers.track.entityCollected(operation: operation, entity: entity)
                                    continuation.yield(entity)
                                } else {
                                    await providers.track.entitySkipped(operation: operation, entity: entity.ref)
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
