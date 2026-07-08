import Foundation
@testable import StasisClient
import StasisClientLib
import Testing

@Suite("NotifyingOperationExecutor")
struct NotifyingOperationExecutorTests {
    private struct SampleError: Error {}

    @Test("startBackupWithRules notifies start and successful completion with a shared id")
    func backupNotifiesStartAndCompletion() async throws {
        let underlying = MockOperationExecutor()
        let notifications = MockSchedulingNotifications()
        let executor = NotifyingOperationExecutor(underlying: underlying, notifications: notifications)

        _ = await executor.startBackupWithRules(definition: UUID(), rules: [], callback: { _ in })

        let started = try #require(await notifications.operationRunStarted.first)
        #expect(await notifications.operationRunStarted.count == 1)
        #expect(started.operation == .backup)

        await eventually { await notifications.operationRunCompleted.count == 1 }
        let completed = try #require(await notifications.operationRunCompleted.first)
        #expect(completed.operation == .backup)
        #expect(completed.failure == nil)
        #expect(completed.id == started.id)
    }

    @Test("recovery notifies with the recovery operation type")
    func recoveryNotifiesRecovery() async throws {
        let underlying = MockOperationExecutor()
        let notifications = MockSchedulingNotifications()
        let executor = NotifyingOperationExecutor(underlying: underlying, notifications: notifications)

        _ = await executor.startRecoveryWithDefinition(
            definition: UUID(), until: nil,
            entities: nil, sources: [],
            destination: nil,
            callback: { _ in }
        )

        let started = try #require(await notifications.operationRunStarted.first)
        #expect(started.operation == .recovery)
        await eventually { await notifications.operationRunCompleted.first?.operation == .recovery }
    }

    @Test("a failing operation reports the failure in the completion notification")
    func failureIsReported() async throws {
        let underlying = MockOperationExecutor()
        await underlying.setCallbackError(SampleError())
        let notifications = MockSchedulingNotifications()
        let executor = NotifyingOperationExecutor(underlying: underlying, notifications: notifications)

        _ = await executor.startBackupWithRules(definition: UUID(), rules: [], callback: { _ in })

        await eventually { await notifications.operationRunCompleted.count == 1 }
        let completed = try #require(await notifications.operationRunCompleted.first)
        let failure = try #require(completed.failure)
        #expect(failure is SampleError)
    }

    @Test("resumeBackup notifies a backup start")
    func resumeNotifiesBackup() async throws {
        let underlying = MockOperationExecutor()
        let notifications = MockSchedulingNotifications()
        let executor = NotifyingOperationExecutor(underlying: underlying, notifications: notifications)

        _ = await executor.resumeBackup(operation: UUID(), callback: { _ in })

        let started = try #require(await notifications.operationRunStarted.first)
        #expect(started.operation == .backup)
    }

    @Test("stop delegates without emitting notifications")
    func stopDoesNotNotify() async throws {
        let underlying = MockOperationExecutor()
        let notifications = MockSchedulingNotifications()
        let executor = NotifyingOperationExecutor(underlying: underlying, notifications: notifications)

        let operation = UUID()
        try await executor.stop(operation: operation)

        #expect(await underlying.stopCalls == [operation])
        #expect(await notifications.operationRunStarted.isEmpty)
    }
}
