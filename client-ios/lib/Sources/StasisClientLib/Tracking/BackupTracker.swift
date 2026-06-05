import Foundation

public protocol BackupTracker: Sendable {
    func started(operation: OperationId, definition: DatasetDefinitionId) async
    func entityDiscovered(operation: OperationId, entity: URL) async
    func specificationProcessed(operation: OperationId, unmatched: [(Rule, Error)]) async
    func entityExamined(operation: OperationId, entity: URL) async
    func entitySkipped(operation: OperationId, entity: URL) async
    func entityCollected(operation: OperationId, entity: SourceEntity) async
    func entityProcessingStarted(operation: OperationId, entity: URL, expectedParts: Int) async
    func entityPartProcessed(operation: OperationId, entity: URL) async
    func entityProcessed(operation: OperationId, entity: URL, metadata: Either<EntityMetadata, EntityMetadata>) async
    func metadataCollected(operation: OperationId) async
    func metadataPushed(operation: OperationId, entry: DatasetEntryId) async
    func failureEncountered(operation: OperationId, failure: Error) async
    func failureEncountered(operation: OperationId, entity: URL, failure: Error) async
    func completed(operation: OperationId) async

    func stateOf(operation: OperationId) async -> BackupState?
}
