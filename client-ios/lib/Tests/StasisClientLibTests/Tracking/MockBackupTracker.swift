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

    func started(operation: OperationId, definition: DatasetDefinitionId) async { counter.increment(.started) }
    func entityDiscovered(operation: OperationId, entity: EntityRef) async { counter.increment(.entityDiscovered) }
    func specificationProcessed(operation: OperationId, unmatched: [(Rule, any Error)]) async {
        counter.increment(.specificationProcessed)
    }
    func entityExamined(operation: OperationId, entity: EntityRef) async { counter.increment(.entityExamined) }
    func entitySkipped(operation: OperationId, entity: EntityRef) async { counter.increment(.entitySkipped) }
    func entityCollected(operation: OperationId, entity: SourceEntity) async { counter.increment(.entityCollected) }
    func entityProcessingStarted(operation: OperationId, entity: EntityRef, expectedParts: Int) async {
        counter.increment(.entityProcessingStarted)
    }
    func entityPartProcessed(operation: OperationId, entity: EntityRef) async { counter.increment(.entityPartProcessed) }
    func entityProcessed(operation: OperationId, entity: EntityRef, metadata: Either<EntityMetadata, EntityMetadata>) async {
        counter.increment(.entityProcessed)
    }
    func metadataCollected(operation: OperationId) async { counter.increment(.metadataCollected) }
    func metadataPushed(operation: OperationId, entry: DatasetEntryId) async { counter.increment(.metadataPushed) }
    func failureEncountered(operation: OperationId, failure: any Error) async { counter.increment(.failureEncountered) }
    func failureEncountered(operation: OperationId, entity: EntityRef, failure: any Error) async {
        counter.increment(.failureEncountered)
    }
    func completed(operation: OperationId) async { counter.increment(.completed) }
    func stateOf(operation: OperationId) async -> BackupState? { nil }
}
