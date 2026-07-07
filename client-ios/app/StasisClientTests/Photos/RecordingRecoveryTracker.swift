import Foundation
import StasisClientLib

final class RecordingRecoveryTracker: RecoveryTracker, Sendable {
    func started(operation: OperationId) async {}
    func entityExamined(
        operation: OperationId,
        entity: EntityRef,
        metadataChanged: Bool,
        contentChanged: Bool
    ) async {}
    func entityCollected(operation: OperationId, entity: TargetEntity) async {}
    func entityProcessingStarted(operation: OperationId, entity: EntityRef, expectedParts: Int) async {}
    func entityPartProcessed(operation: OperationId, entity: EntityRef) async {}
    func entityProcessed(operation: OperationId, entity: EntityRef) async {}
    func metadataApplied(operation: OperationId, entity: EntityRef) async {}
    func failureEncountered(operation: OperationId, failure: Error) async {}
    func failureEncountered(operation: OperationId, entity: EntityRef, failure: Error) async {}
    func completed(operation: OperationId) async {}
    func stateOf(operation: OperationId) async -> RecoveryState? { nil }
}
