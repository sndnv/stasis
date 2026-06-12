import Foundation
@testable import StasisClient
import StasisClientLib
import StasisClientLibTestSupport
import Testing

@MainActor
@Suite("OperationDetailModel")
struct OperationDetailModelTests {
    @Test("refresh loads backup state from the backup tracker")
    func refreshLoadsBackup() async throws {
        let api = StasisClientLibTestSupport.MockServerApiEndpointClient()
        let executor = MockOperationExecutor()
        let session = try TestSession.make(api: api, executor: executor)
        let trackers = try TestDefaults.trackers()
        let backupId = UUID()
        let definitionId = UUID()
        await trackers.backup.started(operation: backupId, definition: definitionId)
        let model = OperationDetailModel(
            key: OperationDetailKey(id: backupId, type: .backup),
            session: session,
            trackers: trackers
        )

        await model.refresh()

        #expect(model.backup?.operation == backupId)
        #expect(model.backup?.definition == definitionId)
        #expect(model.recovery == nil)
        #expect(model.isLoading == false)
    }

    @Test("refresh loads recovery state from the recovery tracker")
    func refreshLoadsRecovery() async throws {
        let api = StasisClientLibTestSupport.MockServerApiEndpointClient()
        let executor = MockOperationExecutor()
        let session = try TestSession.make(api: api, executor: executor)
        let trackers = try TestDefaults.trackers()
        let recoveryId = UUID()
        await trackers.recovery.started(operation: recoveryId)
        let model = OperationDetailModel(
            key: OperationDetailKey(id: recoveryId, type: .recovery),
            session: session,
            trackers: trackers
        )

        await model.refresh()

        #expect(model.recovery?.operation == recoveryId)
        #expect(model.backup == nil)
        #expect(model.isLoading == false)
    }

    @Test("refresh resolves definition info for backups")
    func refreshResolvesDefinitionInfo() async throws {
        let api = StasisClientLibTestSupport.MockServerApiEndpointClient()
        let definition = TestGenerators.definition(info: "Documents")
        await api.setDatasetDefinitionsOverride([definition])
        let executor = MockOperationExecutor()
        let session = try TestSession.make(api: api, executor: executor)
        let trackers = try TestDefaults.trackers()
        let backupId = UUID()
        await trackers.backup.started(operation: backupId, definition: definition.id)
        let model = OperationDetailModel(
            key: OperationDetailKey(id: backupId, type: .backup),
            session: session,
            trackers: trackers
        )

        await model.refresh()

        #expect(model.definitionInfo == "Documents")
    }

    @Test("isActive reflects the executor active state")
    func isActiveReflectsExecutor() async throws {
        let api = StasisClientLibTestSupport.MockServerApiEndpointClient()
        let executor = MockOperationExecutor()
        let backupId = UUID()
        await executor.setActiveOverride([backupId: .backup])
        let session = try TestSession.make(api: api, executor: executor)
        let trackers = try TestDefaults.trackers()
        await trackers.backup.started(operation: backupId, definition: UUID())
        let model = OperationDetailModel(
            key: OperationDetailKey(id: backupId, type: .backup),
            session: session,
            trackers: trackers
        )

        await model.refresh()

        #expect(model.isActive == true)
    }

    @Test("isActive is false when the executor does not report the operation")
    func isActiveFalseWhenNotReported() async throws {
        let api = StasisClientLibTestSupport.MockServerApiEndpointClient()
        let executor = MockOperationExecutor()
        let session = try TestSession.make(api: api, executor: executor)
        let trackers = try TestDefaults.trackers()
        let backupId = UUID()
        await trackers.backup.started(operation: backupId, definition: UUID())
        let model = OperationDetailModel(
            key: OperationDetailKey(id: backupId, type: .backup),
            session: session,
            trackers: trackers
        )

        await model.refresh()

        #expect(model.isActive == false)
    }

    @Test("refresh leaves state nil when the tracker has no entry for the id")
    func refreshLeavesStateNilForUnknownId() async throws {
        let api = StasisClientLibTestSupport.MockServerApiEndpointClient()
        let executor = MockOperationExecutor()
        let session = try TestSession.make(api: api, executor: executor)
        let trackers = try TestDefaults.trackers()
        let model = OperationDetailModel(
            key: OperationDetailKey(id: UUID(), type: .backup),
            session: session,
            trackers: trackers
        )

        await model.refresh()

        #expect(model.backup == nil)
        #expect(model.recovery == nil)
        #expect(model.isLoading == false)
    }

    @Test("clearError resets the error to nil")
    func clearErrorResets() async throws {
        let api = StasisClientLibTestSupport.MockServerApiEndpointClient()
        let executor = MockOperationExecutor()
        let session = try TestSession.make(api: api, executor: executor)
        let trackers = try TestDefaults.trackers()
        let model = OperationDetailModel(
            key: OperationDetailKey(id: UUID(), type: .backup),
            session: session,
            trackers: trackers
        )

        model.clearError()

        #expect(model.error == nil)
    }
}
