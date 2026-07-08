import Foundation
import Synchronization

public final class Recovery: Operation {
    public let id: OperationId
    public let type: OperationType = .recovery

    private let descriptor: Descriptor
    private let providers: RecoveryProviders
    private let taskRef = Mutex<Task<Void, Error>?>(nil)

    public init(descriptor: Descriptor, providers: RecoveryProviders) {
        self.id = Operations.generateId()
        self.descriptor = descriptor
        self.providers = providers
    }

    public func start() async throws {
        let task = try taskRef.withLock { existing in
            guard existing == nil else {
                throw RecoveryError.alreadyStarted(id: id)
            }
            let task = Task { try await self.runPipeline() }
            existing = task
            return task
        }
        try await task.value
    }

    public func stop() {
        guard let task = taskRef.withLock({ $0 }) else {
            preconditionFailure("Recovery [\(id)] not started")
        }
        task.cancel()
    }

    private func runPipeline() async throws {
        do {
            await providers.track.started(operation: id)

            let collection = EntityCollection(
                targetMetadata: descriptor.targetMetadata,
                keep: descriptor.keep(),
                destination: descriptor.destination.toTargetEntityDestination(),
                providers: providers
            )
            let processing = EntityProcessing(
                deviceSecret: descriptor.deviceSecret,
                providers: providers
            )
            let metadataApplication = MetadataApplication(providers: providers)

            let entities = collection.collect(operation: id)
            let processed = processing.process(operation: id, entities: entities)
            let applied = metadataApplication.apply(operation: id, entities: processed)

            for try await _ in applied {}

            await providers.track.completed(operation: id)
        } catch {
            await providers.track.failureEncountered(operation: id, failure: error)
            await providers.analytics.recordFailure(error)
            throw error
        }
    }

    public struct Descriptor: Sendable {
        public let targetMetadata: DatasetMetadata
        public let entities: Set<String>?
        public let sources: Set<RecoverySourceKind>
        public let destination: Destination?
        public let deviceSecret: DeviceSecret

        public init(
            targetMetadata: DatasetMetadata,
            entities: Set<String>?,
            sources: Set<RecoverySourceKind>,
            destination: Destination?,
            deviceSecret: DeviceSecret
        ) {
            self.targetMetadata = targetMetadata
            self.entities = entities
            self.sources = sources
            self.destination = destination
            self.deviceSecret = deviceSecret
        }

        public func keep() -> @Sendable (String, FilesystemMetadata.EntityState) -> Bool {
            let entitiesRef = entities
            let sourcesRef = sources
            return { entity, _ in
                (entitiesRef?.contains(entity) ?? true) && sourcesRef.contains(RecoverySourceKind.forEntity(entity))
            }
        }

        public enum Collector: Sendable {
            case withDefinition(definition: DatasetDefinitionId, until: Date?)
            case withEntry(entry: DatasetEntryId)
        }

        public static func build(
            entities: Set<String>?,
            sources: Set<RecoverySourceKind>,
            destination: Destination?,
            collector: Collector,
            deviceSecret: DeviceSecret,
            providers: RecoveryProviders
        ) async throws -> Descriptor {
            let api = try await providers.clients.api()
            let entry: DatasetEntry
            switch collector {
            case .withDefinition(let definition, let until):
                guard let latest = try await api.latestEntry(definition: definition, until: until) else {
                    throw RecoveryDescriptorError.noEntryForDefinition(definition: definition)
                }
                entry = latest
            case .withEntry(let entryId):
                entry = try await api.datasetEntry(entry: entryId)
            }
            let metadata = try await api.datasetMetadata(entry: entry)
            return Descriptor(
                targetMetadata: metadata,
                entities: entities,
                sources: sources,
                destination: destination,
                deviceSecret: deviceSecret
            )
        }
    }

    public struct Destination: Sendable, Equatable, Hashable {
        public let path: String
        public let keepStructure: Bool

        public init(path: String, keepStructure: Bool) {
            self.path = path
            self.keepStructure = keepStructure
        }
    }
}

extension Optional where Wrapped == Recovery.Destination {
    public func toTargetEntityDestination() -> TargetEntity.Destination {
        switch self {
        case .none:
            return .default
        case .some(let destination):
            return .directory(
                path: URL(fileURLWithPath: destination.path),
                keepDefaultStructure: destination.keepStructure
            )
        }
    }
}

public enum RecoveryError: Error, Equatable, LocalizedError {
    case alreadyStarted(id: OperationId)

    public var errorDescription: String? {
        switch self {
        case .alreadyStarted(let id):
            "Recovery [\(id.uuidString)] already started"
        }
    }
}

public enum RecoveryDescriptorError: Error, Equatable, LocalizedError {
    case noEntryForDefinition(definition: DatasetDefinitionId)

    public var errorDescription: String? {
        switch self {
        case .noEntryForDefinition(let definition):
            "No entry found for definition [\(definition.uuidString)]"
        }
    }
}
