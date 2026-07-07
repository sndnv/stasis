import Foundation

extension Recovery {
    public struct MetadataApplication: Sendable {
        public let providers: RecoveryProviders

        public init(providers: RecoveryProviders) {
            self.providers = providers
        }

        public func apply(
            operation: OperationId,
            entities: AsyncThrowingStream<TargetEntity, Error>
        ) -> AsyncThrowingStream<TargetEntity, Error> {
            AsyncThrowingStream { continuation in
                let task = Task {
                    do {
                        for try await entity in entities {
                            try await RecoveryEntityKinds.applyMetadata(
                                kinds: providers.kinds,
                                entity: entity,
                                providers: providers
                            )
                            await providers.track.metadataApplied(
                                operation: operation,
                                entity: entity.destinationRef
                            )
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
}
