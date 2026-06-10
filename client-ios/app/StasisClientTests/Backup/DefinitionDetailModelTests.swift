import Foundation
@testable import StasisClient
import StasisClientLib
import StasisClientLibTestSupport
import Testing

@MainActor
@Suite("DefinitionDetailModel")
struct DefinitionDetailModelTests {
    @Test("load populates entries sorted by created descending")
    func loadSortsByCreatedDescending() async throws {
        let api = StasisClientLibTestSupport.MockServerApiEndpointClient()
        let definition = TestGenerators.definition()
        let older = makeEntry(definition: definition.id, created: Date(timeIntervalSince1970: 100))
        let newer = makeEntry(definition: definition.id, created: Date(timeIntervalSince1970: 200))
        await api.setDatasetEntriesOverride([older, newer])
        let session = try TestSession.make(api: api)
        let model = DefinitionDetailModel(
            session: session,
            ruleRepository: try inMemoryRuleRepository(),
            definition: definition
        )

        await model.load()

        #expect(model.isLoadingEntries == false)
        #expect(model.entries.map(\.id) == [newer.id, older.id])
    }

    private func makeEntry(definition: DatasetDefinitionId, created: Date) -> DatasetEntry {
        DatasetEntry(
            id: UUID(), definition: definition, device: UUID(),
            data: [], metadata: UUID(), changes: 0, size: 0, created: created
        )
    }

    @Test("startBackup invokes executor with rules from repository")
    func startBackupInvokesExecutor() async throws {
        let api = StasisClientLibTestSupport.MockServerApiEndpointClient()
        let executor = MockOperationExecutor()
        let session = try TestSession.make(api: api, executor: executor)
        let definition = TestGenerators.definition()
        let model = DefinitionDetailModel(
            session: session,
            ruleRepository: try inMemoryRuleRepository(),
            definition: definition
        )

        await model.startBackup()

        let calls = await executor.calls
        #expect(calls.count == 1)
        if case let .backupRules(callDefinition, _) = calls.first {
            #expect(callDefinition == definition.id)
        } else {
            Issue.record("expected backupRules call")
        }
        #expect(model.startingBackup == false)
    }

    @Test("deleteEntry calls API and reloads entries")
    func deleteEntryReloads() async throws {
        let api = StasisClientLibTestSupport.MockServerApiEndpointClient()
        let session = try TestSession.make(api: api)
        let definition = TestGenerators.definition()
        let model = DefinitionDetailModel(
            session: session,
            ruleRepository: try inMemoryRuleRepository(),
            definition: definition
        )
        await model.load()
        let initialCalls = await api.calls
        let baseline = initialCalls.entriesRetrieved

        await model.deleteEntry(UUID())

        let calls = await api.calls
        #expect(calls.entryDeleted == 1)
        #expect(calls.entriesRetrieved == baseline + 1)
    }

    @Test("load surfaces error when datasetEntries throws")
    func loadSurfacesError() async throws {
        let api = StasisClientLibTestSupport.MockServerApiEndpointClient()
        await api.setDatasetEntriesFailure(AccessDeniedFailure())
        let session = try TestSession.make(api: api)
        let definition = TestGenerators.definition()
        let model = DefinitionDetailModel(
            session: session,
            ruleRepository: try inMemoryRuleRepository(),
            definition: definition
        )

        await model.load()

        #expect(model.error != nil)
        #expect(model.entries.isEmpty)
        #expect(model.isLoadingEntries == false)
    }

    @Test("clearError resets the error to nil")
    func clearErrorResets() async throws {
        let api = StasisClientLibTestSupport.MockServerApiEndpointClient()
        await api.setDatasetEntriesFailure(AccessDeniedFailure())
        let session = try TestSession.make(api: api)
        let model = DefinitionDetailModel(
            session: session,
            ruleRepository: try inMemoryRuleRepository(),
            definition: TestGenerators.definition()
        )
        await model.load()
        #expect(model.error != nil)

        model.clearError()

        #expect(model.error == nil)
    }

    private func inMemoryRuleRepository() throws -> RuleRepository {
        RuleRepository(modelContainer: try PersistenceSchema.inMemoryContainer())
    }
}
