import Foundation

extension Backup {
    public struct EntityProcessing: Sendable {
        public let targetDataset: DatasetDefinition
        public let deviceSecret: DeviceSecret
        public let providers: BackupProviders
        public let maxPartSize: Int64

        public init(
            targetDataset: DatasetDefinition,
            deviceSecret: DeviceSecret,
            providers: BackupProviders,
            maxPartSize: Int64
        ) {
            self.targetDataset = targetDataset
            self.deviceSecret = deviceSecret
            self.providers = providers
            self.maxPartSize = maxPartSize
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
                                providers.track.entityProcessed(
                                    operation: operation,
                                    entity: entity.path,
                                    metadata: result
                                )
                                continuation.yield(result)
                            } catch let failure as EndpointFailure {
                                providers.track.failureEncountered(
                                    operation: operation,
                                    entity: entity.path,
                                    failure: failure
                                )
                                await providers.analytics.recordFailure(failure)
                                throw failure
                            } catch {
                                providers.track.failureEncountered(
                                    operation: operation,
                                    entity: entity.path,
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
            let file = try Self.expectFileMetadata(entity: entity)
            let staged = try await stage(operation: operation, entity: entity, checksum: file.checksum)
            let crates = try await push(staged: staged)
            await discard(staged: staged)
            var updatedCrates: [String: UUID] = [:]
            for (partFile, crate) in crates {
                updatedCrates[partFile] = crate
            }
            return .file(EntityMetadata.File(
                path: file.path,
                link: file.link,
                isHidden: file.isHidden,
                created: file.created,
                updated: file.updated,
                owner: file.owner,
                group: file.group,
                permissions: file.permissions,
                size: file.size,
                checksum: file.checksum,
                crates: updatedCrates,
                compression: file.compression
            ))
        }

        func processMetadataChanged(operation: OperationId, entity: SourceEntity) async throws -> EntityMetadata {
            providers.track.entityProcessingStarted(operation: operation, entity: entity.path, expectedParts: 0)
            return entity.currentMetadata
        }

        func stage(
            operation: OperationId,
            entity: SourceEntity,
            checksum: Data
        ) async throws -> [(file: String, path: URL)] {
            providers.track.entityProcessingStarted(
                operation: operation,
                entity: entity.path,
                expectedParts: try Self.expectedParts(entity: entity, withMaximumPartSize: maximumPartSize)
            )

            let encoder = try providers.compression.encoderFor(entity: entity)
            let raw = try Data(contentsOf: entity.path)
            let compressed = try encoder.compress(raw)

            let source = AsyncThrowingStream<Data, Error> { continuation in
                continuation.yield(compressed)
                continuation.finish()
            }

            let entityPath = entity.path.path
            let deviceSecret = self.deviceSecret

            return try await PartitionedSource(
                source: source,
                providers: providers,
                withPartSecret: { partId in
                    deviceSecret.toFileSecret(forFile: "\(entityPath)__part=\(partId)", checksum: checksum)
                },
                onPartStaged: {
                    providers.track.entityPartProcessed(operation: operation, entity: entity.path)
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

        public static func expectFileMetadata(entity: SourceEntity) throws -> EntityMetadata.File {
            switch entity.currentMetadata {
            case .file(let file):
                return file
            case .directory(let directory):
                throw EntityProcessingError.expectedFileGotDirectory(path: directory.path)
            }
        }

        public static func expectedParts(entity: SourceEntity, withMaximumPartSize: Int64) throws -> Int {
            guard withMaximumPartSize > 0 else {
                throw EntityProcessingError.invalidMaximumPartSize(withMaximumPartSize)
            }
            switch entity.currentMetadata {
            case .file(let file):
                guard entity.hasContentChanged else { return 0 }
                let fullParts = Int(file.size / withMaximumPartSize)
                return file.size % withMaximumPartSize == 0 ? fullParts : fullParts + 1
            case .directory:
                return 0
            }
        }
    }

    public enum EntityProcessingError: Error, Equatable {
        case expectedFileGotDirectory(path: String)
        case invalidMaximumPartSize(Int64)
    }
}
