import Foundation

extension Backup {
    public struct MetadataCollection: Sendable {
        public let latestEntry: DatasetEntry?
        public let latestMetadata: DatasetMetadata?
        public let providers: BackupProviders

        public init(
            latestEntry: DatasetEntry?,
            latestMetadata: DatasetMetadata?,
            providers: BackupProviders
        ) {
            self.latestEntry = latestEntry
            self.latestMetadata = latestMetadata
            self.providers = providers
        }

        public func collect(
            operation: OperationId,
            entities: AsyncThrowingStream<Either<EntityMetadata, EntityMetadata>, Error>,
            existingState: BackupState?
        ) -> AsyncThrowingStream<DatasetMetadata, Error> {
            AsyncThrowingStream { continuation in
                let task = Task {
                    let existing = existingState?.asMetadataChanges()
                    var contentChanged: [String: EntityMetadata] = existing?.contentChanged ?? [:]
                    var metadataChanged: [String: EntityMetadata] = existing?.metadataChanged ?? [:]

                    var collectError: Error?
                    do {
                        for try await item in entities {
                            switch item {
                            case .left(let value): contentChanged[value.path] = value
                            case .right(let value): metadataChanged[value.path] = value
                            }
                        }
                    } catch {
                        collectError = error
                    }

                    let allChanges = Set(contentChanged.keys).union(metadataChanged.keys)
                    let filesystem: FilesystemMetadata
                    if let entry = latestEntry, let latest = latestMetadata {
                        filesystem = latest.filesystem.updated(changes: allChanges, latestEntry: entry.id)
                    } else {
                        filesystem = FilesystemMetadata(changes: allChanges)
                    }

                    let metadata = DatasetMetadata(
                        contentChanged: contentChanged,
                        metadataChanged: metadataChanged,
                        filesystem: filesystem
                    )

                    providers.track.metadataCollected(operation: operation)
                    continuation.yield(metadata)

                    if let collectError {
                        continuation.finish(throwing: collectError)
                    } else {
                        continuation.finish()
                    }
                }
                continuation.onTermination = { _ in task.cancel() }
            }
        }
    }
}
