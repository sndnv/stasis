import Foundation

extension Recovery {
    public struct EntityCollection: Sendable {
        public let collector: any RecoveryCollector
        public let providers: RecoveryProviders

        public init(collector: any RecoveryCollector, providers: RecoveryProviders) {
            self.collector = collector
            self.providers = providers
        }

        public func collect(operation: OperationId) -> AsyncThrowingStream<TargetEntity, Error> {
            AsyncThrowingStream { continuation in
                let task = Task {
                    do {
                        for try await entity in collector.collect() {
                            await providers.track.entityExamined(
                                operation: operation,
                                entity: entity.path,
                                metadataChanged: entity.hasChanged,
                                contentChanged: entity.hasContentChanged
                            )
                            if entity.hasChanged {
                                await providers.track.entityCollected(operation: operation, entity: entity)
                                continuation.yield(entity)
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
