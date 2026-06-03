import Foundation
@testable import StasisClientLib
import Testing

@Suite("BackupState")
struct BackupStateTests {
    private struct TestFailure: LocalizedError {
        let message: String
        var errorDescription: String? { message }
    }

    private let entity1 = URL(fileURLWithPath: Fixtures.Metadata.fileOne.path)
    private let entity2 = URL(fileURLWithPath: Fixtures.Metadata.fileTwo.path)
    private let entity3 = URL(fileURLWithPath: Fixtures.Metadata.fileThree.path)

    private var sourceEntity1: SourceEntity {
        try! SourceEntity(
            path: entity1, existingMetadata: nil, currentMetadata: Fixtures.Metadata.fileOne
        )
    }

    private var sourceEntity3: SourceEntity {
        try! SourceEntity(
            path: entity3, existingMetadata: nil, currentMetadata: Fixtures.Metadata.fileThree
        )
    }

    @Test("provides its type and state")
    func providesTypeAndState() {
        let backup = BackupState.start(operation: Operations.generateId(), definition: UUID())
        #expect(backup.type == .backup)
        #expect(backup.completed == nil)
        #expect(backup.backupCompleted().completed != nil)
    }

    @Test("collects operation progress information across all transitions")
    func collectsOperationProgressInformation() {
        let backup = BackupState.start(operation: Operations.generateId(), definition: UUID())
        #expect(backup.entities == BackupState.Entities.empty())
        #expect(backup.metadataCollected == nil)
        #expect(backup.metadataPushed == nil)
        #expect(backup.failures.isEmpty)
        #expect(backup.completed == nil)

        let completed = backup
            .entityDiscovered(entity: entity1)
            .entityDiscovered(entity: entity2)
            .entityDiscovered(entity: entity3)
            .specificationProcessed(unmatched: ["a", "b", "c"])
            .entityExamined(entity: entity1)
            .entityExamined(entity: entity2)
            .entityExamined(entity: entity3)
            .entityCollected(entity: sourceEntity1)
            .entitySkipped(entity: entity2)
            .failureEncountered(failure: TestFailure(message: "Test failure #1"))
            .entityCollected(entity: sourceEntity3)
            .entityProcessingStarted(entity: entity1, expectedParts: 3)
            .entityPartProcessed(entity: entity1)
            .entityPartProcessed(entity: entity1)
            .entityProcessingStarted(entity: entity2, expectedParts: 1)
            .entityPartProcessed(entity: entity1)
            .entityProcessed(entity: entity1, metadata: .left(Fixtures.Metadata.fileOne))
            .entityFailed(entity: entity2, reason: TestFailure(message: "Test failure #2"))
            .backupMetadataCollected()
            .backupMetadataPushed()
            .backupCompleted()

        #expect(completed.operation == backup.operation)
        #expect(completed.entities.discovered == [entity1, entity2, entity3])
        #expect(completed.entities.unmatched == ["a", "b", "c"])
        #expect(completed.entities.examined == [entity1, entity2, entity3])
        #expect(completed.entities.skipped == [entity2])
        #expect(completed.entities.collected == [
            entity1: sourceEntity1,
            entity3: sourceEntity3
        ])
        #expect(completed.entities.pending == [
            entity2: BackupState.PendingSourceEntity(expectedParts: 1, processedParts: 0)
        ])
        #expect(completed.entities.processed == [
            entity1: BackupState.ProcessedSourceEntity(
                expectedParts: 3, processedParts: 3, metadata: .left(Fixtures.Metadata.fileOne)
            )
        ])
        #expect(completed.entities.failed == [entity2: "TestFailure - Test failure #2"])

        #expect(completed.metadataCollected != nil)
        #expect(completed.metadataPushed != nil)
        #expect(completed.failures == ["TestFailure - Test failure #1"])
        #expect(completed.completed != nil)
    }

    @Test("reports entities that have not been processed")
    func reportsRemainingEntities() {
        let backup = BackupState
            .start(operation: Operations.generateId(), definition: UUID())
            .entityDiscovered(entity: sourceEntity1.path)
            .entityDiscovered(entity: sourceEntity3.path)
            .entityProcessed(entity: entity1, metadata: .left(Fixtures.Metadata.fileOne))

        #expect(backup.remainingEntities() == [sourceEntity3.path])
        #expect(backup.backupCompleted().remainingEntities() == [])
    }

    @Test("groups processed entities into content-changed vs metadata-changed maps")
    func providesEntitiesAsMetadataChanges() {
        let backup = BackupState
            .start(operation: Operations.generateId(), definition: UUID())
            .entityProcessed(entity: entity1, metadata: .right(Fixtures.Metadata.fileOne))
            .entityProcessed(entity: entity2, metadata: .left(Fixtures.Metadata.fileTwo))
            .entityProcessed(entity: entity3, metadata: .right(Fixtures.Metadata.fileThree))

        let (contentChanged, metadataChanged) = backup.asMetadataChanges()
        #expect(contentChanged == [Fixtures.Metadata.fileTwo.path: Fixtures.Metadata.fileTwo])
        #expect(metadataChanged == [
            Fixtures.Metadata.fileOne.path: Fixtures.Metadata.fileOne,
            Fixtures.Metadata.fileThree.path: Fixtures.Metadata.fileThree
        ])
    }

    @Test("extracts a pending backup's progress snapshot")
    func extractsPendingBackupProgress() {
        let backup = BackupState
            .start(operation: Operations.generateId(), definition: UUID())
            .entityDiscovered(entity: entity1)
            .entityDiscovered(entity: entity2)
            .entityDiscovered(entity: entity3)
            .entityExamined(entity: entity1)
            .entityExamined(entity: entity2)
            .entityExamined(entity: entity3)
            .entityCollected(entity: sourceEntity1)
            .entitySkipped(entity: entity2)
            .entityCollected(entity: sourceEntity3)
            .entityProcessingStarted(entity: entity1, expectedParts: 1)
            .entityPartProcessed(entity: entity1)
            .entityProcessed(entity: entity3, metadata: .left(Fixtures.Metadata.fileOne))
            .entityFailed(entity: entity2, reason: TestFailure(message: "Test failure #1"))
            .failureEncountered(failure: TestFailure(message: "Test failure #2"))
            .backupMetadataCollected()
            .backupMetadataPushed()
            .backupCompleted()

        #expect(backup.asProgress() == OperationProgress(
            started: backup.started, total: 3, processed: 2, failures: 2, completed: backup.completed
        ))
    }

    @Test("round-trips through the protobuf bridge")
    func roundTripsThroughProtobuf() throws {
        let fixture = Fixtures.State.backupOneState
        let restored = try BackupState.from(operation: fixture.operation, proto: fixture.proto)
        #expect(restored == fixture)
    }

    @Test("fails to be deserialized when no entities are provided")
    func failsWhenNoEntitiesProvided() {
        var proto = Fixtures.State.backupOneState.proto
        proto.clearEntities()
        #expect(throws: BackupStateError.missingEntities) {
            _ = try BackupState.from(operation: Fixtures.State.backupOneState.operation, proto: proto)
        }
    }

    @Test("fails to be deserialized when the definition UUID is malformed")
    func failsWhenDefinitionInvalid() {
        var proto = Fixtures.State.backupOneState.proto
        proto.definition = "not-a-uuid"
        #expect(throws: BackupStateError.invalidDefinition("not-a-uuid")) {
            _ = try BackupState.from(operation: Fixtures.State.backupOneState.operation, proto: proto)
        }
    }

    @Test("fails when a source entity is missing its current metadata wrapper")
    func failsWhenSourceCurrentMetadataMissing() {
        var proto = Fixtures.State.backupOneState.proto
        var entities = proto.entities
        entities.collected = entities.collected.mapValues { value in
            var copy = value
            copy.clearCurrentMetadata()
            return copy
        }
        proto.entities = entities
        #expect(throws: BackupStateError.missingCurrentSourceMetadata) {
            _ = try BackupState.from(operation: Fixtures.State.backupOneState.operation, proto: proto)
        }
    }

    @Test("fails when a source entity's current metadata has no entity case")
    func failsWhenSourceCurrentMetadataHasNoEntity() {
        var proto = Fixtures.State.backupOneState.proto
        var entities = proto.entities
        entities.collected = entities.collected.mapValues { value in
            var copy = value
            copy.currentMetadata = Stasis_ClientIos_Lib_Model_Proto_EntityMetadata()
            return copy
        }
        proto.entities = entities
        #expect(throws: EntityMetadataError.missingEntity) {
            _ = try BackupState.from(operation: Fixtures.State.backupOneState.operation, proto: proto)
        }
    }

    @Test("fails when a processed source entity has neither left nor right metadata")
    func failsWhenProcessedHasNoMetadata() {
        var proto = Fixtures.State.backupOneState.proto
        var entities = proto.entities
        entities.processed = entities.processed.mapValues { value in
            var copy = value
            copy.metadata = nil
            return copy
        }
        proto.entities = entities
        #expect(throws: BackupStateError.missingProcessedSourceEntityMetadata) {
            _ = try BackupState.from(operation: Fixtures.State.backupOneState.operation, proto: proto)
        }
    }
}

@Suite("BackupState.PendingSourceEntity")
struct PendingSourceEntityTests {
    @Test("increments processedParts")
    func incrementsProcessedParts() {
        let entity = BackupState.PendingSourceEntity(expectedParts: 5, processedParts: 1)
        let incremented = entity.inc().inc()
        #expect(incremented == BackupState.PendingSourceEntity(expectedParts: 5, processedParts: 3))
    }

    @Test("converts to a processed entity preserving counts")
    func convertsToProcessed() {
        let pending = BackupState.PendingSourceEntity(expectedParts: 5, processedParts: 1)
        let processed = pending.toProcessed(withMetadata: .left(Fixtures.Metadata.fileOne))
        #expect(processed == BackupState.ProcessedSourceEntity(
            expectedParts: 5, processedParts: 1, metadata: .left(Fixtures.Metadata.fileOne)
        ))
    }
}
