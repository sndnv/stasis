import Foundation
@testable import StasisClient
import StasisClientLib
import StasisClientLibTestSupport
import Testing

@MainActor
@Suite("OperationsModel")
struct OperationsModelTests {
    @Test("refresh populates operations from tracker snapshots")
    func refreshPopulatesFromSnapshots() async throws {
        let api = StasisClientLibTestSupport.MockServerApiEndpointClient()
        let executor = MockOperationExecutor()
        let session = try TestSession.make(api: api, executor: executor)
        let trackers = try TestDefaults.trackers()
        let backupId = UUID()
        let recoveryId = UUID()
        await trackers.backup.started(operation: backupId, definition: UUID())
        await trackers.recovery.started(operation: recoveryId)
        let model = OperationsModel(session: session, trackers: trackers)

        await model.refresh()

        #expect(model.isLoading == false)
        #expect(model.operations.count == 2)
        #expect(model.operations.map(\.id).contains(backupId))
        #expect(model.operations.map(\.id).contains(recoveryId))
    }

    @Test("refresh sorts operations by started descending")
    func refreshSortsByStartedDesc() async throws {
        let api = StasisClientLibTestSupport.MockServerApiEndpointClient()
        let session = try TestSession.make(api: api)
        let trackers = try TestDefaults.trackers()
        let older = UUID()
        let newer = UUID()
        await trackers.backup.started(operation: older, definition: UUID())
        try await Task.sleep(nanoseconds: 5_000_000)
        await trackers.backup.started(operation: newer, definition: UUID())
        let model = OperationsModel(session: session, trackers: trackers)

        await model.refresh()

        #expect(model.operations.first?.id == newer)
        #expect(model.operations.last?.id == older)
    }

    @Test("status is active when executor reports the operation as active")
    func statusActiveWhenExecutorActive() async throws {
        let api = StasisClientLibTestSupport.MockServerApiEndpointClient()
        let executor = MockOperationExecutor()
        let backupId = UUID()
        await executor.setActiveOverride([backupId: .backup])
        let session = try TestSession.make(api: api, executor: executor)
        let trackers = try TestDefaults.trackers()
        await trackers.backup.started(operation: backupId, definition: UUID())
        let model = OperationsModel(session: session, trackers: trackers)

        await model.refresh()

        #expect(model.operations.first?.status == .active)
    }

    @Test("status is completed when the underlying state is completed")
    func statusCompletedWhenStateCompleted() async throws {
        let api = StasisClientLibTestSupport.MockServerApiEndpointClient()
        let executor = MockOperationExecutor()
        let session = try TestSession.make(api: api, executor: executor)
        let trackers = try TestDefaults.trackers()
        let backupId = UUID()
        await trackers.backup.started(operation: backupId, definition: UUID())
        await trackers.backup.completed(operation: backupId)
        let model = OperationsModel(session: session, trackers: trackers)

        await model.refresh()

        if case .completed = model.operations.first?.status {
            // pass
        } else {
            Issue.record("expected .completed, got \(String(describing: model.operations.first?.status))")
        }
    }

    @Test("status is stopped when not active and not completed")
    func statusStoppedWhenNotActiveNotCompleted() async throws {
        let api = StasisClientLibTestSupport.MockServerApiEndpointClient()
        let executor = MockOperationExecutor()
        let session = try TestSession.make(api: api, executor: executor)
        let trackers = try TestDefaults.trackers()
        let backupId = UUID()
        await trackers.backup.started(operation: backupId, definition: UUID())
        let model = OperationsModel(session: session, trackers: trackers)

        await model.refresh()

        #expect(model.operations.first?.status == .stopped)
    }

    @Test("refresh enriches backup summaries with definition info")
    func refreshEnrichesDefinitionInfo() async throws {
        let api = StasisClientLibTestSupport.MockServerApiEndpointClient()
        let definition = TestGenerators.definition(info: "Photos")
        await api.setDatasetDefinitionsOverride([definition])
        let executor = MockOperationExecutor()
        let session = try TestSession.make(api: api, executor: executor)
        let trackers = try TestDefaults.trackers()
        let backupId = UUID()
        await trackers.backup.started(operation: backupId, definition: definition.id)
        let model = OperationsModel(session: session, trackers: trackers)

        await model.refresh()

        #expect(model.operations.first?.definitionInfo == "Photos")
    }

    @Test("refresh still populates operations when datasetDefinitions fails")
    func refreshHandlesDefinitionsError() async throws {
        let api = StasisClientLibTestSupport.MockServerApiEndpointClient()
        await api.setDatasetDefinitionsFailure(AccessDeniedFailure())
        let executor = MockOperationExecutor()
        let session = try TestSession.make(api: api, executor: executor)
        let trackers = try TestDefaults.trackers()
        let backupId = UUID()
        await trackers.backup.started(operation: backupId, definition: UUID())
        let model = OperationsModel(session: session, trackers: trackers)

        await model.refresh()

        #expect(model.operations.count == 1)
        #expect(model.operations.first?.definitionInfo == nil)
        #expect(model.error != nil)
    }

    @Test("stop forwards to the executor and refreshes active state")
    func stopForwardsToExecutor() async throws {
        let api = StasisClientLibTestSupport.MockServerApiEndpointClient()
        let executor = MockOperationExecutor()
        let backupId = UUID()
        await executor.setActiveOverride([backupId: .backup])
        let session = try TestSession.make(api: api, executor: executor)
        let trackers = try TestDefaults.trackers()
        await trackers.backup.started(operation: backupId, definition: UUID())
        let model = OperationsModel(session: session, trackers: trackers)
        await model.refresh()
        await executor.setActiveOverride([:])

        await model.stop(backupId)

        let stops = await executor.stopCalls
        #expect(stops == [backupId])
        #expect(model.operations.first?.status == .stopped)
    }

    @Test("stop sets an error when the executor throws")
    func stopSurfacesError() async throws {
        let api = StasisClientLibTestSupport.MockServerApiEndpointClient()
        let executor = MockOperationExecutor()
        await executor.setStopError(AccessDeniedFailure())
        let session = try TestSession.make(api: api, executor: executor)
        let trackers = try TestDefaults.trackers()
        let model = OperationsModel(session: session, trackers: trackers)

        await model.stop(UUID())

        #expect(model.error != nil)
    }

    @Test("resume forwards to the executor for backup ids")
    func resumeForwardsToExecutor() async throws {
        let api = StasisClientLibTestSupport.MockServerApiEndpointClient()
        let executor = MockOperationExecutor()
        let session = try TestSession.make(api: api, executor: executor)
        let trackers = try TestDefaults.trackers()
        let model = OperationsModel(session: session, trackers: trackers)
        let backupId = UUID()

        await model.resume(backupId, type: .backup)

        let resumes = await executor.resumeCalls
        #expect(resumes == [backupId])
        #expect(model.error == nil)
    }

    @Test("resume is a no-op for non-backup ids")
    func resumeNoOpForNonBackup() async throws {
        let api = StasisClientLibTestSupport.MockServerApiEndpointClient()
        let executor = MockOperationExecutor()
        let session = try TestSession.make(api: api, executor: executor)
        let trackers = try TestDefaults.trackers()
        let model = OperationsModel(session: session, trackers: trackers)

        await model.resume(UUID(), type: .recovery)

        let resumes = await executor.resumeCalls
        #expect(resumes.isEmpty)
    }

    @Test("remove dispatches to the backup tracker for backup ids")
    func removeDispatchesBackup() async throws {
        let api = StasisClientLibTestSupport.MockServerApiEndpointClient()
        let executor = MockOperationExecutor()
        let session = try TestSession.make(api: api, executor: executor)
        let trackers = try TestDefaults.trackers()
        let backupId = UUID()
        await trackers.backup.started(operation: backupId, definition: UUID())
        let model = OperationsModel(session: session, trackers: trackers)

        await model.remove(backupId, type: .backup)
        await model.refresh()

        #expect(await trackers.backup.stateOf(operation: backupId) == nil)
        #expect(model.operations.isEmpty)
    }

    @Test("remove dispatches to the recovery tracker for recovery ids")
    func removeDispatchesRecovery() async throws {
        let api = StasisClientLibTestSupport.MockServerApiEndpointClient()
        let executor = MockOperationExecutor()
        let session = try TestSession.make(api: api, executor: executor)
        let trackers = try TestDefaults.trackers()
        let recoveryId = UUID()
        await trackers.recovery.started(operation: recoveryId)
        let model = OperationsModel(session: session, trackers: trackers)

        await model.remove(recoveryId, type: .recovery)
        await model.refresh()

        #expect(await trackers.recovery.stateOf(operation: recoveryId) == nil)
        #expect(model.operations.isEmpty)
    }

    @Test("clearError resets the error to nil")
    func clearErrorResets() async throws {
        let api = StasisClientLibTestSupport.MockServerApiEndpointClient()
        await api.setDatasetDefinitionsFailure(AccessDeniedFailure())
        let executor = MockOperationExecutor()
        let session = try TestSession.make(api: api, executor: executor)
        let trackers = try TestDefaults.trackers()
        let model = OperationsModel(session: session, trackers: trackers)
        await model.refresh()
        #expect(model.error != nil)

        model.clearError()

        #expect(model.error == nil)
    }
}
