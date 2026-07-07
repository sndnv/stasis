import Foundation

public protocol BackupTracker: Sendable {
    func started(operation: OperationId, definition: DatasetDefinitionId) async
    func entityDiscovered(operation: OperationId, entity: EntityRef) async
    func specificationProcessed(operation: OperationId, unmatched: [(Rule, Error)]) async
    func entityExamined(operation: OperationId, entity: EntityRef) async
    func entitySkipped(operation: OperationId, entity: EntityRef) async
    func entityCollected(operation: OperationId, entity: SourceEntity) async
    func entityProcessingStarted(operation: OperationId, entity: EntityRef, expectedParts: Int) async
    func entityPartProcessed(operation: OperationId, entity: EntityRef) async
    func entityProcessed(operation: OperationId, entity: EntityRef, metadata: Either<EntityMetadata, EntityMetadata>) async
    func metadataCollected(operation: OperationId) async
    func metadataPushed(operation: OperationId, entry: DatasetEntryId) async
    func failureEncountered(operation: OperationId, failure: Error) async
    func failureEncountered(operation: OperationId, entity: EntityRef, failure: Error) async
    func completed(operation: OperationId) async

    func stateOf(operation: OperationId) async -> BackupState?
}
