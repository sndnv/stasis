import Foundation

extension Backup {
    public struct EntityProcessing: Sendable {
        public let targetDataset: DatasetDefinition
        public let deviceSecret: DeviceSecret
        public let providers: BackupProviders
        public let maxPartSize: Int64
        public let maxChunkSize: Int

        public init(
            targetDataset: DatasetDefinition,
            deviceSecret: DeviceSecret,
            providers: BackupProviders,
            maxPartSize: Int64,
            maxChunkSize: Int
        ) {
            self.targetDataset = targetDataset
            self.deviceSecret = deviceSecret
            self.providers = providers
            self.maxPartSize = maxPartSize
            self.maxChunkSize = maxChunkSize
        }

        public var maximumPartSize: Int64 {
            min(maxPartSize, providers.encryptor.maxPlaintextSize)
        }

        public func process(
            operation: OperationId,
            entities: AsyncThrowingStream<SourceEntity, Error>
        ) -> AsyncThrowingStream<Either<EntityMetadata, EntityMetadata>, Error> {
            AsyncThrowingStream { continuation in
                let task = Task {
                    do {
                        for try await entity in entities {
                            do {
                                let result: Either<EntityMetadata, EntityMetadata>
                                if entity.hasContentChanged {
                                    result = .left(try await processContentChanged(operation: operation, entity: entity))
                                } else {
                                    result = .right(try await processMetadataChanged(operation: operation, entity: entity))
                                }
                                await providers.track.entityProcessed(
                                    operation: operation,
                                    entity: entity.ref,
                                    metadata: result
                                )
                                continuation.yield(result)
                            } catch let failure as EndpointFailure {
                                await providers.track.failureEncountered(
                                    operation: operation,
                                    entity: entity.ref,
                                    failure: failure
                                )
                                await providers.analytics.recordFailure(failure)
                                throw failure
                            } catch {
                                await providers.track.failureEncountered(
                                    operation: operation,
                                    entity: entity.ref,
                                    failure: error
                                )
                                await providers.analytics.recordFailure(error)
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

        func processContentChanged(operation: OperationId, entity: SourceEntity) async throws -> EntityMetadata {
            let content = try Self.expectContentMetadata(entity: entity)
            let staged = try await stage(operation: operation, entity: entity, checksum: content.checksum)
            let crates = try await push(staged: staged)
            await discard(staged: staged)
            var updatedCrates: [String: UUID] = [:]
            for (partFile, crate) in crates {
                updatedCrates[partFile] = crate
            }
            return entity.currentMetadata.withCrates(updatedCrates)
        }

        func processMetadataChanged(operation: OperationId, entity: SourceEntity) async throws -> EntityMetadata {
            await providers.track.entityProcessingStarted(operation: operation, entity: entity.ref, expectedParts: 0)
            return entity.currentMetadata
        }

        func stage(
            operation: OperationId,
            entity: SourceEntity,
            checksum: Data
        ) async throws -> [(file: String, path: URL)] {
            await providers.track.entityProcessingStarted(
                operation: operation,
                entity: entity.ref,
                expectedParts: try Self.expectedParts(entity: entity, withMaximumPartSize: maximumPartSize)
            )

            let encoder = try providers.compression.encoderFor(entity: entity)
            let source = try BackupEntityKinds.read(kinds: providers.kinds, entity: entity, chunkSize: maxChunkSize)
            let compressed = encoder.encode(source)

            let entityPath = entity.ref.key
            let deviceSecret = self.deviceSecret

            return try await PartitionedSource(
                source: compressed,
                providers: providers,
                withPartSecret: { partId in
                    deviceSecret.toFileSecret(forFile: "\(entityPath)__part=\(partId)", checksum: checksum)
                },
                onPartStaged: {
                    await providers.track.entityPartProcessed(operation: operation, entity: entity.ref)
                },
                maximumPartSize: maximumPartSize
            ).partitionAndStage()
        }

        func push(staged: [(file: String, path: URL)]) async throws -> [(String, CrateId)] {
            var results: [(String, CrateId)] = []
            do {
                let core = try await providers.clients.core()
                for (partFile, stagedPath) in staged {
                    let crate = UUID()
                    let content = try Data(contentsOf: stagedPath)
                    let manifest = Manifest(
                        crate: crate,
                        size: Int64(content.count),
                        copies: targetDataset.redundantCopies,
                        origin: core.selfNode,
                        source: core.selfNode
                    )
                    try await core.push(manifest: manifest, content: content)
                    results.append((partFile, crate))
                }
                return results
            } catch {
                await discard(staged: staged)
                throw error
            }
        }

        func discard(staged: [(file: String, path: URL)]) async {
            for (_, stagedPath) in staged {
                try? await providers.staging.discard(file: stagedPath)
            }
        }

        public static func expectContentMetadata(entity: SourceEntity) throws -> any EntityContentMetadata {
            guard let content = entity.currentMetadata.content else {
                throw EntityProcessingError.expectedFileGotDirectory(path: entity.currentMetadata.path)
            }
            return content
        }

        public static func expectedParts(entity: SourceEntity, withMaximumPartSize: Int64) throws -> Int {
            guard withMaximumPartSize > 0 else {
                throw EntityProcessingError.invalidMaximumPartSize(withMaximumPartSize)
            }
            guard let content = entity.currentMetadata.content, entity.hasContentChanged else {
                return 0
            }
            let fullParts = Int(content.size / withMaximumPartSize)
            return content.size % withMaximumPartSize == 0 ? fullParts : fullParts + 1
        }
    }

    public enum EntityProcessingError: Error, Equatable, LocalizedError {
        case expectedFileGotDirectory(path: String)
        case invalidMaximumPartSize(Int64)

        public var errorDescription: String? {
            switch self {
            case .expectedFileGotDirectory(let path):
                "Expected metadata for file but directory metadata for [\(path)] provided"
            case .invalidMaximumPartSize(let size):
                "Invalid [maximumPartSize] provided: [\(size)]"
            }
        }
    }
}
