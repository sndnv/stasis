import Foundation
import Synchronization

public final class Backup: Operation {
    public let id: OperationId
    public let type: OperationType = .backup

    private let descriptor: Descriptor
    private let providers: BackupProviders
    private let taskRef = Mutex<Task<Void, Error>?>(nil)

    public init(descriptor: Descriptor, providers: BackupProviders) {
        self.id = descriptor.collector.existingState()?.operation ?? Operations.generateId()
        self.descriptor = descriptor
        self.providers = providers
    }

    public func start() async throws {
        let task = try taskRef.withLock { existing in
            guard existing == nil else {
                throw BackupError.alreadyStarted(id: id)
            }
            let task = Task { try await self.runPipeline() }
            existing = task
            return task
        }
        try await task.value
    }

    public func stop() {
        guard let task = taskRef.withLock({ $0 }) else {
            preconditionFailure("Backup [\(id)] not started")
        }
        task.cancel()
    }

    private func runPipeline() async throws {
        do {
            if case .withState = descriptor.collector {
                // resuming — do not re-track started
            } else {
                await providers.track.started(operation: id, definition: descriptor.targetDataset.id)
            }

            let discovery = EntityDiscovery(
                collector: descriptor.collector.asDiscoveryCollector(),
                latestMetadata: descriptor.latestMetadata,
                providers: providers
            )
            let collection = EntityCollection(
                targetDataset: descriptor.targetDataset,
                providers: providers
            )
            let processing = EntityProcessing(
                targetDataset: descriptor.targetDataset,
                deviceSecret: descriptor.deviceSecret,
                providers: providers,
                maxPartSize: descriptor.limits.maxPartSize,
                maxChunkSize: descriptor.limits.maxChunkSize
            )
            let metadataCollection = MetadataCollection(
                latestEntry: descriptor.latestEntry,
                latestMetadata: descriptor.latestMetadata,
                providers: providers
            )
            let metadataPush = MetadataPush(
                targetDataset: descriptor.targetDataset,
                deviceSecret: descriptor.deviceSecret,
                providers: providers
            )

            let collectors = discovery.discover(operation: id)
            let entities = collection.collect(operation: id, collectors: collectors)
            let processed = processing.process(operation: id, entities: entities)
            let datasetMetadata = metadataCollection.collect(
                operation: id,
                entities: processed,
                existingState: descriptor.collector.existingState()
            )
            try await metadataPush.push(operation: id, metadata: datasetMetadata)

            await providers.track.completed(operation: id)
        } catch {
            await providers.track.failureEncountered(operation: id, failure: error)
            await providers.analytics.recordFailure(error)
            throw error
        }
    }

    public struct Descriptor: Sendable {
        public let targetDataset: DatasetDefinition
        public let latestEntry: DatasetEntry?
        public let latestMetadata: DatasetMetadata?
        public let deviceSecret: DeviceSecret
        public let collector: Collector
        public let limits: Limits

        public init(
            targetDataset: DatasetDefinition,
            latestEntry: DatasetEntry?,
            latestMetadata: DatasetMetadata?,
            deviceSecret: DeviceSecret,
            collector: Collector,
            limits: Limits
        ) {
            self.targetDataset = targetDataset
            self.latestEntry = latestEntry
            self.latestMetadata = latestMetadata
            self.deviceSecret = deviceSecret
            self.collector = collector
            self.limits = limits
        }

        public enum Collector: Sendable {
            case withRules([Rule])
            case withEntities([URL])
            case withState(BackupState)

            public func asDiscoveryCollector() -> EntityDiscovery.Collector {
                switch self {
                case .withRules(let rules): .withRules(rules)
                case .withEntities(let entities): .withEntities(entities)
                case .withState(let state): .withState(state)
                }
            }

            public func existingState() -> BackupState? {
                if case .withState(let state) = self { return state }
                return nil
            }
        }

        public struct Limits: Sendable, Equatable, Hashable {
            public let maxPartSize: Int64
            public let maxChunkSize: Int

            public init(maxPartSize: Int64, maxChunkSize: Int) {
                self.maxPartSize = maxPartSize
                self.maxChunkSize = maxChunkSize
            }
        }

        public static func build(
            definition: DatasetDefinitionId,
            collector: Collector,
            deviceSecret: DeviceSecret,
            limits: Limits,
            providers: BackupProviders
        ) async throws -> Descriptor {
            let api = try await providers.clients.api()
            let targetDataset = try await api.datasetDefinition(definition: definition)
            let latestEntry = try await api.latestEntry(definition: definition, until: nil)
            let latestMetadata: DatasetMetadata?
            if let latestEntry {
                latestMetadata = try await api.datasetMetadata(entry: latestEntry)
            } else {
                latestMetadata = nil
            }
            return Descriptor(
                targetDataset: targetDataset,
                latestEntry: latestEntry,
                latestMetadata: latestMetadata,
                deviceSecret: deviceSecret,
                collector: collector,
                limits: limits
            )
        }
    }
}

public enum BackupError: Error, Equatable {
    case alreadyStarted(id: OperationId)
}
