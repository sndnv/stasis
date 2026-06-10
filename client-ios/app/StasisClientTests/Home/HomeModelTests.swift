import Foundation
@testable import StasisClient
import StasisClientLib
import StasisClientLibTestSupport
import Testing

@MainActor
@Suite("HomeModel")
struct HomeModelTests {
    @Test("load populates firstDefinition and lastEntry when definitions exist")
    func loadPopulates() async throws {
        let api = StasisClientLibTestSupport.MockServerApiEndpointClient()
        let session = try TestSession.make(api: api)
        let model = try makeModel(session: session)

        await model.load()

        #expect(model.isLoading == false)
        #expect(model.firstDefinition != nil)
        #expect(model.lastEntry != nil)
        let calls = await api.calls
        #expect(calls.definitionsRetrieved == 1)
        #expect(calls.entryLatestRetrieved == 1)
    }

    @Test("load with no definitions leaves firstDefinition and lastEntry nil")
    func loadWithoutDefinitions() async throws {
        let api = StasisClientLibTestSupport.MockServerApiEndpointClient()
        await api.setDatasetDefinitionsOverride([])
        let session = try TestSession.make(api: api)
        let model = try makeModel(session: session)

        await model.load()

        #expect(model.firstDefinition == nil)
        #expect(model.lastEntry == nil)
        let calls = await api.calls
        #expect(calls.definitionsRetrieved == 1)
        #expect(calls.entryLatestRetrieved == 0)
    }

    @Test("startBackup calls executor with first definition and rules")
    func startBackupCallsExecutor() async throws {
        let api = StasisClientLibTestSupport.MockServerApiEndpointClient()
        let executor = MockOperationExecutor()
        let session = try TestSession.make(api: api, executor: executor)
        let model = try makeModel(session: session)
        await model.load()

        await model.startBackup()

        let calls = await executor.calls
        #expect(calls.count == 1)
        if case let .backupRules(definition, _) = calls.first {
            #expect(definition == model.firstDefinition?.id)
        } else {
            Issue.record("expected backupRules call, got \(String(describing: calls.first))")
        }
        #expect(model.startingBackup == false)
        #expect(model.error == nil)
    }

    @Test("startBackup is a no-op when no definition is available")
    func startBackupNoOpWithoutDefinition() async throws {
        let api = StasisClientLibTestSupport.MockServerApiEndpointClient()
        await api.setDatasetDefinitionsOverride([])
        let executor = MockOperationExecutor()
        let session = try TestSession.make(api: api, executor: executor)
        let model = try makeModel(session: session)
        await model.load()

        await model.startBackup()

        let calls = await executor.calls
        #expect(calls.isEmpty)
    }

    @Test("load surfaces an error message when the API throws")
    func loadSurfacesError() async throws {
        let api = StasisClientLibTestSupport.MockServerApiEndpointClient()
        await api.setDatasetDefinitionsFailure(AccessDeniedFailure())
        let session = try TestSession.make(api: api)
        let model = try makeModel(session: session)

        await model.load()

        #expect(model.isLoading == false)
        #expect(model.firstDefinition == nil)
        #expect(model.error != nil)
    }

    @Test("clearError resets the error to nil")
    func clearErrorResets() async throws {
        let api = StasisClientLibTestSupport.MockServerApiEndpointClient()
        await api.setDatasetDefinitionsFailure(AccessDeniedFailure())
        let session = try TestSession.make(api: api)
        let model = try makeModel(session: session)
        await model.load()
        #expect(model.error != nil)

        model.clearError()

        #expect(model.error == nil)
    }

    @Test("load picks the most recent completed operation across backup and recovery trackers")
    func recomputeAcrossTrackers() async throws {
        let api = StasisClientLibTestSupport.MockServerApiEndpointClient()
        let session = try TestSession.make(api: api)
        let trackers = try TestDefaults.trackers()
        let backupId = UUID()
        let recoveryId = UUID()
        await trackers.backup.started(operation: backupId, definition: UUID())
        await trackers.backup.completed(operation: backupId)
        try await Task.sleep(nanoseconds: 5_000_000)
        await trackers.recovery.started(operation: recoveryId)
        await trackers.recovery.completed(operation: recoveryId)
        let model = HomeModel(
            session: session,
            trackers: trackers,
            ruleRepository: RuleRepository(modelContainer: try PersistenceSchema.inMemoryContainer())
        )

        await model.load()

        #expect(model.lastOperation?.id == recoveryId)
        #expect(model.lastOperation?.type == .recovery)
    }

    private func makeModel(session: AuthenticatedSession) throws -> HomeModel {
        HomeModel(
            session: session,
            trackers: try TestDefaults.trackers(),
            ruleRepository: RuleRepository(modelContainer: try PersistenceSchema.inMemoryContainer())
        )
    }
}
