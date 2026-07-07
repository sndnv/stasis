import Foundation
@testable import StasisClient
import StasisClientLib
import Testing

@Suite("DefaultRecoveryTracker")
struct DefaultRecoveryTrackerTests {
    @Test("tracks the full recovery event sequence")
    func trackRecoveryEvents() async throws {
        let tracker = DefaultRecoveryTracker(store: try TestStateStore.recoveries())

        let operation = UUID()
        let file1 = URL(fileURLWithPath: "/tmp/test-1")
        let file2 = URL(fileURLWithPath: "/tmp/test-2")

        let targetEntity = try TargetEntity(
            ref: .filesystem(file1),
            destination: .default,
            existingMetadata: fileMetadata(at: file1.path),
            currentMetadata: nil
        )

        #expect(await tracker.snapshot().isEmpty)

        await tracker.started(operation: operation)
        await tracker.entityExamined(operation: operation, entity: .filesystem(file1), metadataChanged: true, contentChanged: false)
        await tracker.entityExamined(operation: operation, entity: .filesystem(file2), metadataChanged: true, contentChanged: true)
        await tracker.entityCollected(operation: operation, entity: targetEntity)
        await tracker.entityProcessingStarted(operation: operation, entity: .filesystem(file1), expectedParts: 3)
        await tracker.entityPartProcessed(operation: operation, entity: .filesystem(file1))
        await tracker.entityPartProcessed(operation: operation, entity: .filesystem(file1))
        await tracker.entityProcessed(operation: operation, entity: .filesystem(file2))
        await tracker.metadataApplied(operation: operation, entity: .filesystem(file1))
        await tracker.failureEncountered(operation: operation, entity: .filesystem(file1), failure: TestFailure(message: "test failure 1"))
        await tracker.failureEncountered(operation: operation, failure: TestFailure(message: "test failure 2"))
        await tracker.completed(operation: operation)

        let state = try #require(await tracker.snapshot()[operation])
        #expect(state.completed != nil)
        #expect(state.entities.examined.count == 2)
        #expect(state.entities.collected.count == 1)
        #expect(state.entities.pending.count == 1)
        #expect(state.entities.processed.count == 1)
        #expect(state.entities.metadataApplied.count == 1)
        #expect(state.entities.failed.count == 1)
        #expect(state.failures.count == 1)
    }

    @Test("provides per-operation update stream")
    func provideOperationUpdates() async throws {
        let tracker = DefaultRecoveryTracker(store: try TestStateStore.recoveries())
        let operation = UUID()
        let file = URL(fileURLWithPath: "/tmp/test")

        await tracker.started(operation: operation)
        let updates = await tracker.updates(operation: operation)

        await tracker.entityExamined(operation: operation, entity: .filesystem(file), metadataChanged: true, contentChanged: false)
        await tracker.started(operation: UUID())
        await tracker.completed(operation: operation)

        var observed: RecoveryState?
        for await state in updates where state.completed != nil {
            observed = state
            break
        }
        let final = try #require(observed)
        #expect(final.entities.examined.count == 1)
        #expect(final.completed != nil)
    }

    @Test("provides recovery state via stateOf")
    func provideRecoveryState() async throws {
        let tracker = DefaultRecoveryTracker(store: try TestStateStore.recoveries())
        let operation = UUID()
        let file = URL(fileURLWithPath: "/tmp/test")

        await tracker.started(operation: operation)
        await tracker.entityExamined(operation: operation, entity: .filesystem(file), metadataChanged: true, contentChanged: false)

        let state = try #require(await tracker.stateOf(operation: operation))
        #expect(state.entities.examined.count == 1)
        #expect(state.completed == nil)
    }

    @Test("supports removing operations")
    func supportRemovingOperations() async throws {
        let tracker = DefaultRecoveryTracker(store: try TestStateStore.recoveries())
        let operation1 = UUID()
        let operation2 = UUID()

        await tracker.started(operation: operation1)
        await tracker.started(operation: operation2)

        var snapshot = await tracker.snapshot()
        #expect(snapshot[operation1] != nil)
        #expect(snapshot[operation2] != nil)

        await tracker.remove(operation: operation1)

        snapshot = await tracker.snapshot()
        #expect(snapshot[operation1] == nil)
        #expect(snapshot[operation2] != nil)
    }

    @Test("supports clearing all operations")
    func supportClearingOperations() async throws {
        let tracker = DefaultRecoveryTracker(store: try TestStateStore.recoveries())
        let operation1 = UUID()
        let operation2 = UUID()
        let file = URL(fileURLWithPath: "/tmp/test")

        await tracker.started(operation: operation1)
        await tracker.entityExamined(operation: operation1, entity: .filesystem(file), metadataChanged: true, contentChanged: false)
        await tracker.started(operation: operation2)

        var snapshot = await tracker.snapshot()
        #expect(snapshot[operation1] != nil)
        #expect(snapshot[operation2] != nil)

        await tracker.clear()

        snapshot = await tracker.snapshot()
        #expect(snapshot.isEmpty)
    }

    @Test("restores state from store on first access")
    func restoresPreviouslyPersistedState() async throws {
        let store = try TestStateStore.recoveries()
        let operation = UUID()
        let file = URL(fileURLWithPath: "/tmp/restored")

        let first = DefaultRecoveryTracker(store: store)
        await first.started(operation: operation)
        await first.entityExamined(operation: operation, entity: .filesystem(file), metadataChanged: true, contentChanged: false)
        await first.completed(operation: operation)

        let restored = DefaultRecoveryTracker(store: store)
        let state = try #require(await restored.stateOf(operation: operation))
        #expect(state.entities.examined.count == 1)
        #expect(state.completed != nil)
    }

    private func fileMetadata(at path: String) -> EntityMetadata {
        .file(EntityMetadata.File(
            path: path,
            link: nil,
            isHidden: false,
            created: Date(timeIntervalSince1970: 0),
            updated: Date(timeIntervalSince1970: 4_102_444_800),
            owner: "root",
            group: "root",
            permissions: "rwxrwxrwx",
            size: 1,
            checksum: Data([0x01]),
            crates: [:],
            compression: "none"
        ))
    }

    private struct TestFailure: LocalizedError {
        let message: String
        var errorDescription: String? { message }
    }
}
