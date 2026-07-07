import Foundation
@testable import StasisClientLib
import Testing

@Suite("NoOpOperationExecutor")
struct NoOpOperationExecutorTests {
    @Test("active / completed / find return empty")
    func emptyState() async {
        let executor = NoOpOperationExecutor()
        #expect(await executor.active().isEmpty)
        #expect(await executor.completed().isEmpty)
        #expect(await executor.find(operation: UUID()) == nil)
    }

    @Test("backup callbacks fire with notImplemented")
    func backupCallbacksNotImplemented() async {
        let executor = NoOpOperationExecutor()
        await confirmCallback(notImplemented: true) { callback in
            _ = await executor.startBackupWithRules(definition: UUID(), rules: [], callback: callback)
        }
        await confirmCallback(notImplemented: true) { callback in
            _ = await executor.startBackupWithEntities(definition: UUID(), entities: [], callback: callback)
        }
        await confirmCallback(notImplemented: true) { callback in
            _ = await executor.resumeBackup(operation: UUID(), callback: callback)
        }
    }

    @Test("recovery callbacks fire with notImplemented")
    func recoveryCallbacksNotImplemented() async {
        let executor = NoOpOperationExecutor()
        await confirmCallback(notImplemented: true) { callback in
            _ = await executor.startRecoveryWithDefinition(
                definition: UUID(), until: nil, query: nil, destination: nil, callback: callback
            )
        }
        await confirmCallback(notImplemented: true) { callback in
            _ = await executor.startRecoveryWithEntry(
                entry: UUID(), query: nil, destination: nil, callback: callback
            )
        }
    }

    @Test("expiration / validation / keyRotation throw notImplemented")
    func startMethodsThrow() async {
        let executor = NoOpOperationExecutor()
        await assertThrowsNotImplemented { _ = try await executor.startExpiration { _ in } }
        await assertThrowsNotImplemented { _ = try await executor.startValidation { _ in } }
        await assertThrowsNotImplemented { _ = try await executor.startKeyRotation { _ in } }
    }

    @Test("stop throws operationNotFound")
    func stopThrowsNotFound() async {
        let executor = NoOpOperationExecutor()
        let id = UUID()
        await #expect(throws: OperationExecutorError.operationNotFound(id)) {
            try await executor.stop(operation: id)
        }
    }

    private func confirmCallback(
        notImplemented: Bool,
        _ block: (@escaping OperationCallback) async -> Void
    ) async {
        let received = CallbackBox()
        await block { error in
            received.set(error: error)
        }
        let error = received.error
        if notImplemented {
            #expect(error is OperationExecutorError)
            if case .notImplemented = error as? OperationExecutorError {} else {
                Issue.record("expected .notImplemented, got \(String(describing: error))")
            }
        }
    }

    private func assertThrowsNotImplemented(_ block: () async throws -> Void) async {
        do {
            try await block()
            Issue.record("expected throw")
        } catch let error as OperationExecutorError {
            if case .notImplemented = error {} else {
                Issue.record("expected .notImplemented, got \(error)")
            }
        } catch {
            Issue.record("unexpected error \(error)")
        }
    }

    private final class CallbackBox: @unchecked Sendable {
        private(set) var error: (any Error)?
        func set(error: (any Error)?) { self.error = error }
    }
}
