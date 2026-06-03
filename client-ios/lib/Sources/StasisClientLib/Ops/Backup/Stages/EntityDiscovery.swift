import Foundation

extension Backup {
    public struct EntityDiscovery: Sendable {
        public enum Collector: Sendable {
            case withRules([Rule])
            case withEntities([URL])
            case withState(BackupState)
        }

        public let collector: Collector
        public let latestMetadata: DatasetMetadata?
        public let providers: BackupProviders

        public init(
            collector: Collector,
            latestMetadata: DatasetMetadata?,
            providers: BackupProviders
        ) {
            self.collector = collector
            self.latestMetadata = latestMetadata
            self.providers = providers
        }

        public func discover(operation: OperationId) -> AsyncThrowingStream<any BackupCollector, Error> {
            AsyncThrowingStream { continuation in
                let task = Task {
                    let entities: [URL]
                    switch collector {
                    case .withRules(let rules):
                        let spec = Specification.tracked(
                            operation: operation,
                            rules: rules,
                            tracker: providers.track
                        )
                        for parent in spec.includedParents {
                            providers.track.entityDiscovered(operation: operation, entity: parent)
                        }
                        providers.track.specificationProcessed(operation: operation, unmatched: spec.unmatched)
                        entities = spec.included
                    case .withEntities(let provided):
                        let existing = provided.filter { FileManager.default.fileExists(atPath: $0.path) }
                        for entity in existing {
                            providers.track.entityDiscovered(operation: operation, entity: entity)
                        }
                        entities = existing
                    case .withState(let state):
                        entities = state.remainingEntities()
                    }

                    let backupCollector = DefaultBackupCollector(
                        entities: entities,
                        latestMetadata: latestMetadata,
                        metadataCollector: DefaultBackupMetadataCollector(
                            checksum: providers.checksum,
                            compression: providers.compression
                        ),
                        clients: providers.clients
                    )
                    continuation.yield(backupCollector)
                    continuation.finish()
                }
                continuation.onTermination = { _ in task.cancel() }
            }
        }
    }
}
