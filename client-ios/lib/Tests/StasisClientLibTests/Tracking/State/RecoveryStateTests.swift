import Foundation
@testable import StasisClientLib
import Testing

@Suite("RecoveryState")
struct RecoveryStateTests {
    private struct TestFailure: LocalizedError {
        let message: String
        var errorDescription: String? { message }
    }

    private let entity1 = EntityRef.filesystem(URL(fileURLWithPath: Fixtures.Metadata.fileOne.path))
    private let entity2 = EntityRef.filesystem(URL(fileURLWithPath: Fixtures.Metadata.fileTwo.path))
    private let entity3 = EntityRef.filesystem(URL(fileURLWithPath: Fixtures.Metadata.fileThree.path))

    private var targetEntity1: TargetEntity {
        try! TargetEntity(
            ref: entity1, destination: .default,
            existingMetadata: Fixtures.Metadata.fileOne, currentMetadata: nil
        )
    }

    private var targetEntity3: TargetEntity {
        try! TargetEntity(
            ref: entity3, destination: .default,
            existingMetadata: Fixtures.Metadata.fileThree, currentMetadata: nil
        )
    }

    @Test("provides its type and state")
    func providesTypeAndState() {
        let recovery = RecoveryState.start(operation: Operations.generateId())
        #expect(recovery.type == .recovery)
        #expect(recovery.completed == nil)
        #expect(recovery.recoveryCompleted().completed != nil)
    }

    @Test("collects operation progress information across all transitions")
    func collectsOperationProgressInformation() {
        let recovery = RecoveryState.start(operation: Operations.generateId())
        #expect(recovery.entities == RecoveryState.Entities.empty())
        #expect(recovery.failures.isEmpty)
        #expect(recovery.completed == nil)

        let completed = recovery
            .entityExamined(entity: entity1)
            .entityExamined(entity: entity2)
            .entityExamined(entity: entity3)
            .entityCollected(entity: targetEntity1)
            .entityCollected(entity: targetEntity3)
            .entityProcessingStarted(entity: entity1, expectedParts: 1)
            .entityPartProcessed(entity: entity1)
            .entityProcessingStarted(entity: entity3, expectedParts: 3)
            .entityPartProcessed(entity: entity3)
            .entityProcessed(entity: entity1)
            .entityMetadataApplied(entity: entity1)
            .entityFailed(entity: entity3, reason: TestFailure(message: "Test failure #1"))
            .failureEncountered(failure: TestFailure(message: "Test failure #2"))
            .recoveryCompleted()

        #expect(completed.entities.examined == [entity1, entity2, entity3])
        #expect(completed.entities.collected == [entity1: targetEntity1, entity3: targetEntity3])
        #expect(completed.entities.pending == [
            entity3: RecoveryState.PendingTargetEntity(expectedParts: 3, processedParts: 1)
        ])
        #expect(completed.entities.processed == [
            entity1: RecoveryState.ProcessedTargetEntity(expectedParts: 1, processedParts: 1)
        ])
        #expect(completed.entities.metadataApplied == [entity1])
        #expect(completed.entities.failed == [entity3: "TestFailure - Test failure #1"])
        #expect(completed.failures == ["TestFailure - Test failure #2"])
        #expect(completed.completed != nil)
    }

    @Test("extracts a pending recovery's progress snapshot")
    func extractsPendingRecoveryProgress() {
        let recovery = RecoveryState
            .start(operation: Operations.generateId())
            .entityExamined(entity: entity1)
            .entityCollected(entity: targetEntity1)
            .entityCollected(entity: targetEntity3)
            .entityProcessingStarted(entity: entity1, expectedParts: 1)
            .entityPartProcessed(entity: entity1)
            .entityProcessed(entity: entity2)
            .entityMetadataApplied(entity: entity3)
            .entityFailed(entity: entity3, reason: TestFailure(message: "Test failure #1"))
            .failureEncountered(failure: TestFailure(message: "Test failure #2"))
            .recoveryCompleted()

        #expect(recovery.asProgress() == OperationProgress(
            started: recovery.started, total: 1, processed: 1, failures: 2, completed: recovery.completed
        ))
    }

    @Test("round-trips through the protobuf bridge for default destination")
    func roundTripsThroughProtobufDefault() throws {
        let fixture = Fixtures.State.recoveryOneState
        let restored = try RecoveryState.from(operation: fixture.operation, proto: fixture.proto)
        #expect(restored == fixture)
    }

    @Test("round-trips through the protobuf bridge for directory destination")
    func roundTripsThroughProtobufDirectory() throws {
        let fixture = Fixtures.State.recoveryTwoState
        let restored = try RecoveryState.from(operation: fixture.operation, proto: fixture.proto)
        #expect(restored == fixture)
    }

    @Test("fails to be deserialized when no entities are provided")
    func failsWhenNoEntitiesProvided() {
        var proto = Fixtures.State.recoveryOneState.proto
        proto.clearEntities()
        #expect(throws: RecoveryStateError.missingEntities) {
            _ = try RecoveryState.from(operation: Fixtures.State.recoveryOneState.operation, proto: proto)
        }
    }

    @Test("fails when a target entity is missing its existing metadata wrapper")
    func failsWhenTargetExistingMetadataMissing() {
        var proto = Fixtures.State.recoveryOneState.proto
        var entities = proto.entities
        entities.collected = entities.collected.mapValues { value in
            var copy = value
            copy.clearExistingMetadata()
            return copy
        }
        proto.entities = entities
        #expect(throws: RecoveryStateError.missingExistingTargetMetadata) {
            _ = try RecoveryState.from(operation: Fixtures.State.recoveryOneState.operation, proto: proto)
        }
    }

    @Test("fails when a target entity's current metadata has no entity case")
    func failsWhenTargetCurrentMetadataHasNoEntity() {
        var proto = Fixtures.State.recoveryOneState.proto
        var entities = proto.entities
        entities.collected = entities.collected.mapValues { value in
            var copy = value
            copy.currentMetadata = Stasis_ClientIos_Lib_Model_Proto_EntityMetadata()
            return copy
        }
        proto.entities = entities
        #expect(throws: EntityMetadataError.missingEntity) {
            _ = try RecoveryState.from(operation: Fixtures.State.recoveryOneState.operation, proto: proto)
        }
    }
}

@Suite("RecoveryState.PendingTargetEntity")
struct PendingTargetEntityTests {
    @Test("increments processedParts")
    func incrementsProcessedParts() {
        let entity = RecoveryState.PendingTargetEntity(expectedParts: 5, processedParts: 1)
        let incremented = entity.inc().inc()
        #expect(incremented == RecoveryState.PendingTargetEntity(expectedParts: 5, processedParts: 3))
    }
}
