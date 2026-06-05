import Foundation
@testable import StasisClient
import StasisClientLib
import Testing

@Suite("DefaultBackupTracker")
struct DefaultBackupTrackerTests {
    @Test("tracks the full backup event sequence")
    func trackBackupEvents() async throws {
        let tracker = DefaultBackupTracker(store: try TestStateStore.backups())

        let operation = UUID()
        let file1 = URL(fileURLWithPath: "/tmp/test-1")
        let file2 = URL(fileURLWithPath: "/tmp/test-2")

        #expect(await tracker.snapshot().isEmpty)

        let sourceEntity = try SourceEntity(
            path: file1,
            existingMetadata: nil,
            currentMetadata: fileMetadata(at: file1.path)
        )

        await tracker.started(operation: operation, definition: UUID())
        await tracker.entityDiscovered(operation: operation, entity: file1)
        await tracker.entityDiscovered(operation: operation, entity: file2)
        await tracker.specificationProcessed(operation: operation, unmatched: [])
        await tracker.entityExamined(operation: operation, entity: file1)
        await tracker.entityExamined(operation: operation, entity: file2)
        await tracker.entitySkipped(operation: operation, entity: file2)
        await tracker.entityCollected(operation: operation, entity: sourceEntity)
        await tracker.entityProcessingStarted(operation: operation, entity: file2, expectedParts: 3)
        await tracker.entityPartProcessed(operation: operation, entity: file2)
        await tracker.entityPartProcessed(operation: operation, entity: file2)
        await tracker.entityProcessed(operation: operation, entity: file1, metadata: .left(fileMetadata(at: file1.path)))
        await tracker.metadataCollected(operation: operation)
        await tracker.metadataPushed(operation: operation, entry: UUID())
        await tracker.failureEncountered(operation: operation, entity: file1, failure: TestFailure(message: "Test failure 1"))
        await tracker.failureEncountered(operation: operation, failure: TestFailure(message: "Test failure 2"))
        await tracker.completed(operation: operation)

        let state = try #require(await tracker.snapshot()[operation])
        #expect(state.completed != nil)
        #expect(state.entities.discovered.count == 2)
        #expect(state.entities.unmatched.isEmpty)
        #expect(state.entities.examined.count == 2)
        #expect(state.entities.skipped.count == 1)
        #expect(state.entities.collected.count == 1)
        #expect(state.entities.pending.count == 1)
        #expect(state.entities.processed.count == 1)
        #expect(state.entities.failed.count == 1)
        #expect(state.metadataCollected != nil)
        #expect(state.metadataPushed != nil)
        #expect(state.failures.count == 1)
    }

    @Test("formats unmatched rule failures")
    func trackBackupEventsWithUnmatchedRules() async throws {
        let tracker = DefaultBackupTracker(store: try TestStateStore.backups())
        let operation = UUID()

        let rule1 = Rule(id: 0, operation: .include, directory: "/tmp/1", pattern: "*", definition: nil)
        let rule2 = Rule(id: 0, operation: .include, directory: "/tmp/2", pattern: "*", definition: nil)
        let rule3 = Rule(id: 0, operation: .include, directory: "/tmp/3", pattern: "*", definition: nil)

        await tracker.started(operation: operation, definition: UUID())
        await tracker.specificationProcessed(
            operation: operation,
            unmatched: [
                (rule1, TestFailure(message: "Test failure 1")),
                (rule2, TestFailure(message: "Test failure 2")),
                (rule3, TestFailure(message: "Test failure 3"))
            ]
        )

        let state = try #require(await tracker.snapshot()[operation])
        #expect(state.entities.unmatched.sorted() == [
            "Rule [+ /tmp/1 *] failed with [Test failure 1]",
            "Rule [+ /tmp/2 *] failed with [Test failure 2]",
            "Rule [+ /tmp/3 *] failed with [Test failure 3]"
        ])
    }

    @Test("provides per-operation update stream")
    func provideBackupUpdates() async throws {
        let tracker = DefaultBackupTracker(store: try TestStateStore.backups())
        let operation = UUID()
        let file = URL(fileURLWithPath: "/tmp/test")

        await tracker.started(operation: operation, definition: UUID())
        let updates = await tracker.updates(operation: operation)

        await tracker.entityExamined(operation: operation, entity: file)
        await tracker.entitySkipped(operation: operation, entity: file)

        let otherOperation = UUID()
        await tracker.started(operation: otherOperation, definition: UUID())
        await tracker.entityDiscovered(operation: otherOperation, entity: file)
        await tracker.completed(operation: operation)

        var observed: BackupState?
        for await state in updates where state.completed != nil {
            observed = state
            break
        }
        let final = try #require(observed)
        #expect(final.entities.examined.count == 1)
        #expect(final.entities.skipped.count == 1)
        #expect(final.completed != nil)
    }

    @Test("provides backup state via stateOf")
    func provideBackupState() async throws {
        let tracker = DefaultBackupTracker(store: try TestStateStore.backups())
        let operation = UUID()
        let file = URL(fileURLWithPath: "/tmp/test")

        await tracker.started(operation: operation, definition: UUID())
        await tracker.entityExamined(operation: operation, entity: file)

        let state = try #require(await tracker.stateOf(operation: operation))
        #expect(state.entities.examined.count == 1)
        #expect(state.completed == nil)
    }

    @Test("supports removing operations")
    func supportRemovingOperations() async throws {
        let tracker = DefaultBackupTracker(store: try TestStateStore.backups())
        let operation1 = UUID()
        let operation2 = UUID()
        let file = URL(fileURLWithPath: "/tmp/test")

        await tracker.started(operation: operation1, definition: UUID())
        await tracker.entityExamined(operation: operation1, entity: file)
        await tracker.started(operation: operation2, definition: UUID())

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
        let tracker = DefaultBackupTracker(store: try TestStateStore.backups())
        let operation1 = UUID()
        let operation2 = UUID()
        let file = URL(fileURLWithPath: "/tmp/test")

        await tracker.started(operation: operation1, definition: UUID())
        await tracker.entityExamined(operation: operation1, entity: file)
        await tracker.started(operation: operation2, definition: UUID())

        var snapshot = await tracker.snapshot()
        #expect(snapshot[operation1] != nil)
        #expect(snapshot[operation2] != nil)

        await tracker.clear()

        snapshot = await tracker.snapshot()
        #expect(snapshot.isEmpty)
    }

    @Test("restores state from store on first access")
    func restoresPreviouslyPersistedState() async throws {
        let store = try TestStateStore.backups()
        let operation = UUID()
        let definition = UUID()

        let first = DefaultBackupTracker(store: store)
        await first.started(operation: operation, definition: definition)
        await first.entityExamined(operation: operation, entity: URL(fileURLWithPath: "/tmp/a"))
        await first.completed(operation: operation)

        let restored = DefaultBackupTracker(store: store)
        let state = try #require(await restored.stateOf(operation: operation))
        #expect(state.definition == definition)
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
