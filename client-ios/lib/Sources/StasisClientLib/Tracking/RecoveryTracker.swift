import Foundation

public protocol RecoveryTracker: Sendable {
    func started(operation: OperationId) async
    func entityExamined(operation: OperationId, entity: URL, metadataChanged: Bool, contentChanged: Bool) async
    func entityCollected(operation: OperationId, entity: TargetEntity) async
    func entityProcessingStarted(operation: OperationId, entity: URL, expectedParts: Int) async
    func entityPartProcessed(operation: OperationId, entity: URL) async
    func entityProcessed(operation: OperationId, entity: URL) async
    func metadataApplied(operation: OperationId, entity: URL) async
    func failureEncountered(operation: OperationId, failure: Error) async
    func failureEncountered(operation: OperationId, entity: URL, failure: Error) async
    func completed(operation: OperationId) async

    func stateOf(operation: OperationId) async -> RecoveryState?
}
