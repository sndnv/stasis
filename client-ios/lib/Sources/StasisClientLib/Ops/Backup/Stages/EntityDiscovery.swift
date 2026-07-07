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
                    do {
                        await reportUnsupportedSources(operation: operation)
                        for kind in providers.kinds {
                            let backupCollector = try await kind.collector(
                                operation: operation,
                                collector: collector,
                                latestMetadata: latestMetadata,
                                providers: providers
                            )
                            continuation.yield(backupCollector)
                        }
                        continuation.finish()
                    } catch {
                        continuation.finish(throwing: error)
                    }
                }
                continuation.onTermination = { _ in task.cancel() }
            }
        }

        private func reportUnsupportedSources(operation: OperationId) async {
            guard case .withRules(let rules) = collector else { return }
            let handledSchemes: Set<String?> = Set(
                providers.kinds.map { kind in (kind as? any BackupLibraryKind)?.scheme }
            )
            for rule in rules where !handledSchemes.contains(SourceUri.scheme(rule.source)) {
                await providers.track.failureEncountered(
                    operation: operation,
                    failure: RuleParsingFailure("No backup kind was registered for source [\(rule.source)]")
                )
            }
        }
    }
}
