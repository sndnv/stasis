import Foundation

extension Recovery {
    public struct EntityProcessing: Sendable {
        public let deviceSecret: DeviceSecret
        public let providers: RecoveryProviders

        public init(deviceSecret: DeviceSecret, providers: RecoveryProviders) {
            self.deviceSecret = deviceSecret
            self.providers = providers
        }

        public func process(
            operation: OperationId,
            entities: AsyncThrowingStream<TargetEntity, Error>
        ) -> AsyncThrowingStream<TargetEntity, Error> {
            AsyncThrowingStream { continuation in
                let task = Task {
                    do {
                        for try await entity in entities {
                            guard let prepared = try prepare(entity) else { continue }
                            do {
                                if prepared.hasContentChanged {
                                    try await processContentChanged(operation: operation, entity: prepared)
                                } else {
                                    processMetadataChanged(operation: operation, entity: prepared)
                                }
                                providers.track.entityProcessed(operation: operation, entity: prepared.destinationPath)
                                continuation.yield(prepared)
                            } catch let failure as EndpointFailure {
                                providers.track.failureEncountered(
                                    operation: operation,
                                    entity: prepared.path,
                                    failure: failure
                                )
                                await providers.analytics.recordFailure(failure)
                                throw failure
                            } catch {
                                providers.track.failureEncountered(
                                    operation: operation,
                                    entity: prepared.path,
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

        private func prepare(_ entity: TargetEntity) throws -> TargetEntity? {
            if case .directory(_, let keepDefaultStructure) = entity.destination,
               !keepDefaultStructure,
               case .directory = entity.existingMetadata {
                return nil
            }
            try createEntityDirectory(entity)
            return entity
        }

        private func createEntityDirectory(_ entity: TargetEntity) throws {
            let directory: URL = switch entity.existingMetadata {
            case .file: entity.destinationPath.deletingLastPathComponent()
            case .directory: entity.destinationPath
            }
            try FileManager.default.createDirectory(
                at: directory,
                withIntermediateDirectories: true,
                attributes: [.posixPermissions: NSNumber(value: 0o700)]
            )
        }

        private func processContentChanged(operation: OperationId, entity: TargetEntity) async throws {
            let file = try Self.expectFileMetadata(entity: entity)

            providers.track.entityProcessingStarted(
                operation: operation,
                entity: entity.path,
                expectedParts: file.crates.count
            )

            let pulled = try await pull(crates: file.crates, entity: entity.originalPath)
            let decrypted = DecryptedCrates.decrypt(
                pulled,
                withPartSecret: { partPath in
                    deviceSecret.toFileSecret(forFile: partPath, checksum: file.checksum)
                },
                providers: providers
            )
            let merged = try MergedCrates.merge(decrypted, onPartProcessed: {
                providers.track.entityPartProcessed(operation: operation, entity: entity.path)
            })
            let decompressor = try providers.compression.decoderFor(entity: entity)
            let decompressed = DecompressedSource.decompress(merged, decompressor: decompressor)
            try await DestagedByteStringSource.destage(
                decompressed,
                to: entity.destinationPath,
                providers: providers
            )
        }

        private func processMetadataChanged(operation: OperationId, entity: TargetEntity) {
            providers.track.entityProcessingStarted(operation: operation, entity: entity.path, expectedParts: 0)
        }

        private func pull(crates: [String: CrateId], entity: URL) async throws -> [RecoveryCrate] {
            let core = try await providers.clients.core()
            let recovered: [RecoveryCrate] = crates.map { partPath, crate in
                let partId = Self.partIdFromPath(partPath)
                return RecoveryCrate(partId: partId, partPath: partPath) {
                    guard let data = try await core.pull(crate: crate) else {
                        throw RecoveryPullError.crateMissing(crate: crate, entity: entity.path)
                    }
                    return AsyncThrowingStream { continuation in
                        continuation.yield(data)
                        continuation.finish()
                    }
                }
            }
            let lastPartId = recovered.map(\.partId).max() ?? 0
            guard lastPartId + 1 == crates.count else {
                throw RecoveryPullError.unexpectedLastPartId(lastPartId: lastPartId, crateCount: crates.count)
            }
            return recovered
        }

        public static func expectFileMetadata(entity: TargetEntity) throws -> EntityMetadata.File {
            switch entity.existingMetadata {
            case .file(let file):
                return file
            case .directory(let directory):
                throw EntityProcessingError.expectedFileGotDirectory(path: directory.path)
            }
        }

        public static func partIdFromPath(_ partPath: String) -> Int {
            let lastComponent = (partPath as NSString).lastPathComponent
            guard let match = partIdRegex.firstMatch(
                in: lastComponent,
                range: NSRange(lastComponent.startIndex..., in: lastComponent)
            ),
                  match.numberOfRanges > 1,
                  let range = Range(match.range(at: 1), in: lastComponent) else {
                return 0
            }
            return Int(lastComponent[range]) ?? 0
        }

        private static let partIdRegex: NSRegularExpression = {
            // swiftlint:disable:next force_try
            try! NSRegularExpression(pattern: ".*__part=(\\d+)")
        }()
    }

    public enum EntityProcessingError: Error, Equatable {
        case expectedFileGotDirectory(path: String)
    }
}

public enum RecoveryPullError: Error, Equatable {
    case crateMissing(crate: CrateId, entity: String)
    case unexpectedLastPartId(lastPartId: Int, crateCount: Int)
}
