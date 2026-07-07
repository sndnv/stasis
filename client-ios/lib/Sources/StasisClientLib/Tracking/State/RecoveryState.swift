import Foundation

public struct RecoveryState: OperationState, Sendable, Equatable, Hashable {
    public let operation: OperationId
    public let started: Date
    public private(set) var entities: Entities
    public private(set) var failures: [String]
    public private(set) var completed: Date?

    public init(
        operation: OperationId,
        started: Date,
        entities: Entities,
        failures: [String],
        completed: Date?
    ) {
        self.operation = operation
        self.started = started
        self.entities = entities
        self.failures = failures
        self.completed = completed
    }

    public var type: OperationType { .recovery }

    public func entityExamined(entity: EntityRef) -> RecoveryState {
        var copy = self
        copy.entities.examined.insert(entity)
        return copy
    }

    public func entityCollected(entity: TargetEntity) -> RecoveryState {
        var copy = self
        copy.entities.collected[entity.ref] = entity
        return copy
    }

    public func entityProcessingStarted(entity: EntityRef, expectedParts: Int) -> RecoveryState {
        var copy = self
        copy.entities.pending[entity] = PendingTargetEntity(expectedParts: expectedParts, processedParts: 0)
        return copy
    }

    public func entityPartProcessed(entity: EntityRef) -> RecoveryState {
        var copy = self
        copy.entities.pending[entity] = copy.entities.pending[entity]!.inc()
        return copy
    }

    public func entityProcessed(entity: EntityRef) -> RecoveryState {
        var copy = self
        let processed: ProcessedTargetEntity = if let pending = copy.entities.pending[entity] {
            ProcessedTargetEntity(
                expectedParts: pending.expectedParts,
                processedParts: pending.processedParts
            )
        } else {
            ProcessedTargetEntity(expectedParts: 0, processedParts: 0)
        }
        copy.entities.pending.removeValue(forKey: entity)
        copy.entities.processed[entity] = processed
        return copy
    }

    public func entityMetadataApplied(entity: EntityRef) -> RecoveryState {
        var copy = self
        copy.entities.metadataApplied.insert(entity)
        return copy
    }

    public func entityFailed(entity: EntityRef, reason: Error) -> RecoveryState {
        var copy = self
        copy.entities.failed[entity] = reason.tracked
        return copy
    }

    public func failureEncountered(failure: Error) -> RecoveryState {
        var copy = self
        copy.failures.append(failure.tracked)
        return copy
    }

    public func recoveryCompleted() -> RecoveryState {
        var copy = self
        copy.completed = Date()
        return copy
    }

    public func asProgress() -> OperationProgress {
        OperationProgress(
            started: started,
            total: entities.examined.count,
            processed: entities.processed.count,
            failures: entities.failed.count + failures.count,
            completed: completed
        )
    }

    public struct Entities: Sendable, Equatable, Hashable {
        public var examined: Set<EntityRef>
        public var collected: [EntityRef: TargetEntity]
        public var pending: [EntityRef: PendingTargetEntity]
        public var processed: [EntityRef: ProcessedTargetEntity]
        public var metadataApplied: Set<EntityRef>
        public var failed: [EntityRef: String]

        public init(
            examined: Set<EntityRef>,
            collected: [EntityRef: TargetEntity],
            pending: [EntityRef: PendingTargetEntity],
            processed: [EntityRef: ProcessedTargetEntity],
            metadataApplied: Set<EntityRef>,
            failed: [EntityRef: String]
        ) {
            self.examined = examined
            self.collected = collected
            self.pending = pending
            self.processed = processed
            self.metadataApplied = metadataApplied
            self.failed = failed
        }

        public static func empty() -> Entities {
            Entities(
                examined: [], collected: [:], pending: [:], processed: [:],
                metadataApplied: [], failed: [:]
            )
        }
    }

    public struct PendingTargetEntity: Sendable, Equatable, Hashable {
        public let expectedParts: Int
        public let processedParts: Int

        public init(expectedParts: Int, processedParts: Int) {
            self.expectedParts = expectedParts
            self.processedParts = processedParts
        }

        public func inc() -> PendingTargetEntity {
            PendingTargetEntity(expectedParts: expectedParts, processedParts: processedParts + 1)
        }
    }

    public struct ProcessedTargetEntity: Sendable, Equatable, Hashable {
        public let expectedParts: Int
        public let processedParts: Int

        public init(expectedParts: Int, processedParts: Int) {
            self.expectedParts = expectedParts
            self.processedParts = processedParts
        }
    }

    public static func start(operation: OperationId) -> RecoveryState {
        RecoveryState(
            operation: operation,
            started: Date(),
            entities: .empty(),
            failures: [],
            completed: nil
        )
    }
}
