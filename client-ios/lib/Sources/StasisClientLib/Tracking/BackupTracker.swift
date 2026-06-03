import Foundation

public protocol BackupTracker: Sendable {
    func started(operation: OperationId, definition: DatasetDefinitionId)
    func entityDiscovered(operation: OperationId, entity: URL)
    func specificationProcessed(operation: OperationId, unmatched: [(Rule, Error)])
    func entityExamined(operation: OperationId, entity: URL)
    func entitySkipped(operation: OperationId, entity: URL)
    func entityCollected(operation: OperationId, entity: SourceEntity)
    func entityProcessingStarted(operation: OperationId, entity: URL, expectedParts: Int)
    func entityPartProcessed(operation: OperationId, entity: URL)
    func entityProcessed(operation: OperationId, entity: URL, metadata: Either<EntityMetadata, EntityMetadata>)
    func metadataCollected(operation: OperationId)
    func metadataPushed(operation: OperationId, entry: DatasetEntryId)
    func failureEncountered(operation: OperationId, failure: Error)
    func failureEncountered(operation: OperationId, entity: URL, failure: Error)
    func completed(operation: OperationId)

    func stateOf(operation: OperationId) async -> BackupState?
}
