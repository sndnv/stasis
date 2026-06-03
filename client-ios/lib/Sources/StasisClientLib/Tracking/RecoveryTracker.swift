import Foundation

public protocol RecoveryTracker: Sendable {
    func started(operation: OperationId)
    func entityExamined(operation: OperationId, entity: URL, metadataChanged: Bool, contentChanged: Bool)
    func entityCollected(operation: OperationId, entity: TargetEntity)
    func entityProcessingStarted(operation: OperationId, entity: URL, expectedParts: Int)
    func entityPartProcessed(operation: OperationId, entity: URL)
    func entityProcessed(operation: OperationId, entity: URL)
    func metadataApplied(operation: OperationId, entity: URL)
    func failureEncountered(operation: OperationId, failure: Error)
    func failureEncountered(operation: OperationId, entity: URL, failure: Error)
    func completed(operation: OperationId)

    func stateOf(operation: OperationId) async -> RecoveryState?
}
