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
                                    await processMetadataChanged(operation: operation, entity: prepared)
                                }
                                await providers.track.entityProcessed(operation: operation, entity: prepared.destinationRef)
                                continuation.yield(prepared)
                            } catch let failure as EndpointFailure {
                                await providers.track.failureEncountered(
                                    operation: operation,
                                    entity: prepared.ref,
                                    failure: failure
                                )
                                await providers.analytics.recordFailure(failure)
                                throw failure
                            } catch {
                                await providers.track.failureEncountered(
                                    operation: operation,
                                    entity: prepared.ref,
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
            try RecoveryEntityKinds.prepare(kinds: providers.kinds, entity: entity, providers: providers)
            return entity
        }

        private func processContentChanged(operation: OperationId, entity: TargetEntity) async throws {
            let content = try Self.expectContentMetadata(entity: entity)

            await providers.track.entityProcessingStarted(
                operation: operation,
                entity: entity.ref,
                expectedParts: content.crates.count
            )

            let decompressed = try await EntityContent.pull(
                metadata: content,
                entityKey: entity.originalPath.path,
                deviceSecret: deviceSecret,
                clients: providers.clients,
                decryptor: providers.decryptor,
                onPartProcessed: {
                    await providers.track.entityPartProcessed(operation: operation, entity: entity.ref)
                }
            )
            try await RecoveryEntityKinds.write(
                kinds: providers.kinds,
                entity: entity,
                content: decompressed,
                providers: providers
            )
        }

        private func processMetadataChanged(operation: OperationId, entity: TargetEntity) async {
            await providers.track.entityProcessingStarted(operation: operation, entity: entity.ref, expectedParts: 0)
        }

        public static func expectContentMetadata(entity: TargetEntity) throws -> any EntityContentMetadata {
            guard let content = entity.existingMetadata.content else {
                throw EntityProcessingError.expectedFileGotDirectory(path: entity.existingMetadata.path)
            }
            return content
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

    public enum EntityProcessingError: Error, Equatable, LocalizedError {
        case expectedFileGotDirectory(path: String)

        public var errorDescription: String? {
            switch self {
            case .expectedFileGotDirectory(let path):
                "Expected metadata for file but directory metadata for [\(path)] provided"
            }
        }
    }
}

public enum RecoveryPullError: Error, Equatable, LocalizedError {
    case crateMissing(crate: CrateId, entity: String)
    case unexpectedLastPartId(lastPartId: Int, crateCount: Int)

    public var errorDescription: String? {
        switch self {
        case .crateMissing(let crate, let entity):
            "Failed to pull crate [\(crate.uuidString)] for entity [\(entity)]"
        case .unexpectedLastPartId(let lastPartId, let crateCount):
            "Unexpected last part ID [\(lastPartId)] encountered for an entity with [\(crateCount)] crate(s)"
        }
    }
}
