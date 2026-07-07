import Foundation
import StasisClientLib
import Synchronization

final class RecordingBackupTracker: BackupTracker, Sendable {
    private struct Recorded: Sendable {
        var discovered: [EntityRef] = []
        var failures = 0
    }

    private let recorded = Mutex(Recorded())

    var discovered: [EntityRef] { recorded.withLock { $0.discovered } }
    var failures: Int { recorded.withLock { $0.failures } }

    func started(operation: OperationId, definition: DatasetDefinitionId) async {}
    func entityDiscovered(operation: OperationId, entity: EntityRef) async {
        recorded.withLock { $0.discovered.append(entity) }
    }
    func specificationProcessed(operation: OperationId, unmatched: [(Rule, Error)]) async {}
    func entityExamined(operation: OperationId, entity: EntityRef) async {}
    func entitySkipped(operation: OperationId, entity: EntityRef) async {}
    func entityCollected(operation: OperationId, entity: SourceEntity) async {}
    func entityProcessingStarted(operation: OperationId, entity: EntityRef, expectedParts: Int) async {}
    func entityPartProcessed(operation: OperationId, entity: EntityRef) async {}
    func entityProcessed(
        operation: OperationId,
        entity: EntityRef,
        metadata: Either<EntityMetadata, EntityMetadata>
    ) async {}
    func metadataCollected(operation: OperationId) async {}
    func metadataPushed(operation: OperationId, entry: DatasetEntryId) async {}
    func failureEncountered(operation: OperationId, failure: Error) async {
        recorded.withLock { $0.failures += 1 }
    }
    func failureEncountered(operation: OperationId, entity: EntityRef, failure: Error) async {
        recorded.withLock { $0.failures += 1 }
    }
    func completed(operation: OperationId) async {}
    func stateOf(operation: OperationId) async -> BackupState? { nil }
}
