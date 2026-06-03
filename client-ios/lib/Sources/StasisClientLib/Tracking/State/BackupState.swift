import Foundation

public struct BackupState: OperationState, Sendable, Equatable, Hashable {
    public let operation: OperationId
    public let definition: DatasetDefinitionId
    public let started: Date
    public private(set) var entities: Entities
    public private(set) var metadataCollected: Date?
    public private(set) var metadataPushed: Date?
    public private(set) var failures: [String]
    public private(set) var completed: Date?

    public init(
        operation: OperationId,
        definition: DatasetDefinitionId,
        started: Date,
        entities: Entities,
        metadataCollected: Date?,
        metadataPushed: Date?,
        failures: [String],
        completed: Date?
    ) {
        self.operation = operation
        self.definition = definition
        self.started = started
        self.entities = entities
        self.metadataCollected = metadataCollected
        self.metadataPushed = metadataPushed
        self.failures = failures
        self.completed = completed
    }

    public var type: OperationType { .backup }

    public func entityDiscovered(entity: URL) -> BackupState {
        var copy = self
        copy.entities.discovered.insert(entity)
        return copy
    }

    public func specificationProcessed(unmatched: [String]) -> BackupState {
        var copy = self
        copy.entities.unmatched = unmatched
        return copy
    }

    public func entityExamined(entity: URL) -> BackupState {
        var copy = self
        copy.entities.examined.insert(entity)
        return copy
    }

    public func entitySkipped(entity: URL) -> BackupState {
        var copy = self
        copy.entities.skipped.insert(entity)
        return copy
    }

    public func entityCollected(entity: SourceEntity) -> BackupState {
        var copy = self
        copy.entities.collected[entity.path] = entity
        return copy
    }

    public func entityProcessingStarted(entity: URL, expectedParts: Int) -> BackupState {
        var copy = self
        copy.entities.pending[entity] = PendingSourceEntity(expectedParts: expectedParts, processedParts: 0)
        return copy
    }

    public func entityPartProcessed(entity: URL) -> BackupState {
        var copy = self
        copy.entities.pending[entity] = copy.entities.pending[entity]!.inc()
        return copy
    }

    public func entityProcessed(entity: URL, metadata: Either<EntityMetadata, EntityMetadata>) -> BackupState {
        var copy = self
        let processed: ProcessedSourceEntity = if let pending = copy.entities.pending[entity] {
            ProcessedSourceEntity(
                expectedParts: pending.expectedParts,
                processedParts: pending.processedParts,
                metadata: metadata
            )
        } else {
            ProcessedSourceEntity(expectedParts: 0, processedParts: 0, metadata: metadata)
        }
        copy.entities.pending.removeValue(forKey: entity)
        copy.entities.processed[entity] = processed
        return copy
    }

    public func entityFailed(entity: URL, reason: Error) -> BackupState {
        var copy = self
        copy.entities.failed[entity] = reason.tracked
        return copy
    }

    public func backupMetadataCollected() -> BackupState {
        var copy = self
        copy.metadataCollected = Date()
        return copy
    }

    public func backupMetadataPushed() -> BackupState {
        var copy = self
        copy.metadataPushed = Date()
        return copy
    }

    public func failureEncountered(failure: Error) -> BackupState {
        var copy = self
        copy.failures.append(failure.tracked)
        return copy
    }

    public func backupCompleted() -> BackupState {
        var copy = self
        copy.completed = Date()
        return copy
    }

    public func remainingEntities() -> [URL] {
        guard completed == nil else { return [] }
        return entities.discovered.filter { !entities.processed.keys.contains($0) }
    }

    public func asMetadataChanges() -> (
        contentChanged: [String: EntityMetadata],
        metadataChanged: [String: EntityMetadata]
    ) {
        var contentChanged: [String: EntityMetadata] = [:]
        var metadataChanged: [String: EntityMetadata] = [:]
        for (_, processed) in entities.processed {
            switch processed.metadata {
            case .left(let value): contentChanged[value.path] = value
            case .right(let value): metadataChanged[value.path] = value
            }
        }
        return (contentChanged, metadataChanged)
    }

    public func asProgress() -> OperationProgress {
        OperationProgress(
            started: started,
            total: entities.discovered.count,
            processed: entities.skipped.count + entities.processed.count,
            failures: entities.failed.count + failures.count,
            completed: completed
        )
    }

    public struct Entities: Sendable, Equatable, Hashable {
        public var discovered: Set<URL>
        public var unmatched: [String]
        public var examined: Set<URL>
        public var skipped: Set<URL>
        public var collected: [URL: SourceEntity]
        public var pending: [URL: PendingSourceEntity]
        public var processed: [URL: ProcessedSourceEntity]
        public var failed: [URL: String]

        public init(
            discovered: Set<URL>,
            unmatched: [String],
            examined: Set<URL>,
            skipped: Set<URL>,
            collected: [URL: SourceEntity],
            pending: [URL: PendingSourceEntity],
            processed: [URL: ProcessedSourceEntity],
            failed: [URL: String]
        ) {
            self.discovered = discovered
            self.unmatched = unmatched
            self.examined = examined
            self.skipped = skipped
            self.collected = collected
            self.pending = pending
            self.processed = processed
            self.failed = failed
        }

        public static func empty() -> Entities {
            Entities(
                discovered: [], unmatched: [], examined: [], skipped: [],
                collected: [:], pending: [:], processed: [:], failed: [:]
            )
        }
    }

    public struct PendingSourceEntity: Sendable, Equatable, Hashable {
        public let expectedParts: Int
        public let processedParts: Int

        public init(expectedParts: Int, processedParts: Int) {
            self.expectedParts = expectedParts
            self.processedParts = processedParts
        }

        public func inc() -> PendingSourceEntity {
            PendingSourceEntity(expectedParts: expectedParts, processedParts: processedParts + 1)
        }

        public func toProcessed(
            withMetadata metadata: Either<EntityMetadata, EntityMetadata>
        ) -> ProcessedSourceEntity {
            ProcessedSourceEntity(
                expectedParts: expectedParts,
                processedParts: processedParts,
                metadata: metadata
            )
        }
    }

    public struct ProcessedSourceEntity: Sendable, Equatable, Hashable {
        public let expectedParts: Int
        public let processedParts: Int
        public let metadata: Either<EntityMetadata, EntityMetadata>

        public init(expectedParts: Int, processedParts: Int, metadata: Either<EntityMetadata, EntityMetadata>) {
            self.expectedParts = expectedParts
            self.processedParts = processedParts
            self.metadata = metadata
        }
    }

    public static func start(operation: OperationId, definition: DatasetDefinitionId) -> BackupState {
        BackupState(
            operation: operation,
            definition: definition,
            started: Date(),
            entities: .empty(),
            metadataCollected: nil,
            metadataPushed: nil,
            failures: [],
            completed: nil
        )
    }
}
