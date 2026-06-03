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

    public func entityExamined(entity: URL) -> RecoveryState {
        var copy = self
        copy.entities.examined.insert(entity)
        return copy
    }

    public func entityCollected(entity: TargetEntity) -> RecoveryState {
        var copy = self
        copy.entities.collected[entity.path] = entity
        return copy
    }

    public func entityProcessingStarted(entity: URL, expectedParts: Int) -> RecoveryState {
        var copy = self
        copy.entities.pending[entity] = PendingTargetEntity(expectedParts: expectedParts, processedParts: 0)
        return copy
    }

    public func entityPartProcessed(entity: URL) -> RecoveryState {
        var copy = self
        copy.entities.pending[entity] = copy.entities.pending[entity]!.inc()
        return copy
    }

    public func entityProcessed(entity: URL) -> RecoveryState {
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

    public func entityMetadataApplied(entity: URL) -> RecoveryState {
        var copy = self
        copy.entities.metadataApplied.insert(entity)
        return copy
    }

    public func entityFailed(entity: URL, reason: Error) -> RecoveryState {
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
        public var examined: Set<URL>
        public var collected: [URL: TargetEntity]
        public var pending: [URL: PendingTargetEntity]
        public var processed: [URL: ProcessedTargetEntity]
        public var metadataApplied: Set<URL>
        public var failed: [URL: String]

        public init(
            examined: Set<URL>,
            collected: [URL: TargetEntity],
            pending: [URL: PendingTargetEntity],
            processed: [URL: ProcessedTargetEntity],
            metadataApplied: Set<URL>,
            failed: [URL: String]
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
