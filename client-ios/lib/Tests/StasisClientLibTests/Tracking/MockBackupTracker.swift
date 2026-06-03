import Foundation
@testable import StasisClientLib

final class MockBackupTracker: BackupTracker {
    enum Statistic: String, CaseIterable, Sendable {
        case started = "Started"
        case entityDiscovered = "EntityDiscovered"
        case specificationProcessed = "SpecificationProcessed"
        case entityExamined = "EntityExamined"
        case entitySkipped = "EntitySkipped"
        case entityCollected = "EntityCollected"
        case entityProcessingStarted = "EntityProcessingStarted"
        case entityPartProcessed = "EntityPartProcessed"
        case entityProcessed = "EntityProcessed"
        case metadataCollected = "MetadataCollected"
        case metadataPushed = "MetadataPushed"
        case failureEncountered = "FailureEncountered"
        case completed = "Completed"
    }

    private let counter = StatsCounter<Statistic>()

    var statistics: [Statistic: Int] { counter.snapshot }

    func started(operation: OperationId, definition: DatasetDefinitionId) { counter.increment(.started) }
    func entityDiscovered(operation: OperationId, entity: URL) { counter.increment(.entityDiscovered) }
    func specificationProcessed(operation: OperationId, unmatched: [(Rule, any Error)]) {
        counter.increment(.specificationProcessed)
    }
    func entityExamined(operation: OperationId, entity: URL) { counter.increment(.entityExamined) }
    func entitySkipped(operation: OperationId, entity: URL) { counter.increment(.entitySkipped) }
    func entityCollected(operation: OperationId, entity: SourceEntity) { counter.increment(.entityCollected) }
    func entityProcessingStarted(operation: OperationId, entity: URL, expectedParts: Int) {
        counter.increment(.entityProcessingStarted)
    }
    func entityPartProcessed(operation: OperationId, entity: URL) { counter.increment(.entityPartProcessed) }
    func entityProcessed(operation: OperationId, entity: URL, metadata: Either<EntityMetadata, EntityMetadata>) {
        counter.increment(.entityProcessed)
    }
    func metadataCollected(operation: OperationId) { counter.increment(.metadataCollected) }
    func metadataPushed(operation: OperationId, entry: DatasetEntryId) { counter.increment(.metadataPushed) }
    func failureEncountered(operation: OperationId, failure: any Error) { counter.increment(.failureEncountered) }
    func failureEncountered(operation: OperationId, entity: URL, failure: any Error) {
        counter.increment(.failureEncountered)
    }
    func completed(operation: OperationId) { counter.increment(.completed) }
    func stateOf(operation: OperationId) async -> BackupState? { nil }
}
