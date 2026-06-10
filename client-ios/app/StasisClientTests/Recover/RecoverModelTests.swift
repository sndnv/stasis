import Foundation
@testable import StasisClient
import StasisClientLib
import StasisClientLibTestSupport
import Testing

@MainActor
@Suite("RecoverModel")
struct RecoverModelTests {
    @Test("load filters to definitions with a latest entry, sorted by info")
    func loadFiltersAndSorts() async throws {
        let api = StasisClientLibTestSupport.MockServerApiEndpointClient()
        let nonEmpty = TestGenerators.definition(info: "alpha")
        let empty = TestGenerators.definition(info: "beta")
        await api.setDatasetDefinitionsOverride([empty, nonEmpty])
        await api.setLatestEntryOverride(nonEmpty.id, UUID())
        await api.setLatestEntryOverride(empty.id, nil)
        let session = try TestSession.make(api: api)
        let model = RecoverModel(session: session)

        await model.load()

        #expect(model.definitions.count == 1)
        #expect(model.definitions.first?.id == nonEmpty.id)
        #expect(model.isLoadingDefinitions == false)
    }

    @Test("loadEntriesIfNeeded fetches once per definition")
    func loadEntriesOncePerDefinition() async throws {
        let api = StasisClientLibTestSupport.MockServerApiEndpointClient()
        let session = try TestSession.make(api: api)
        let model = RecoverModel(session: session)
        let definition = UUID()

        await model.loadEntriesIfNeeded(for: definition)
        let firstCalls = await api.calls
        await model.loadEntriesIfNeeded(for: definition)
        let secondCalls = await api.calls

        #expect(firstCalls.entriesRetrieved == 1)
        #expect(secondCalls.entriesRetrieved == 1)
        #expect(!model.entries.isEmpty)
    }

    @Test("loadEntriesIfNeeded reloads when the definition changes")
    func loadEntriesReloadsOnChange() async throws {
        let api = StasisClientLibTestSupport.MockServerApiEndpointClient()
        let session = try TestSession.make(api: api)
        let model = RecoverModel(session: session)

        await model.loadEntriesIfNeeded(for: UUID())
        await model.loadEntriesIfNeeded(for: UUID())

        let calls = await api.calls
        #expect(calls.entriesRetrieved == 2)
    }

    @Test("resetEntries clears entries and forces reload on next call")
    func resetEntriesForcesReload() async throws {
        let api = StasisClientLibTestSupport.MockServerApiEndpointClient()
        let session = try TestSession.make(api: api)
        let model = RecoverModel(session: session)
        let definition = UUID()
        await model.loadEntriesIfNeeded(for: definition)

        model.resetEntries()
        #expect(model.entries.isEmpty)
        await model.loadEntriesIfNeeded(for: definition)

        let calls = await api.calls
        #expect(calls.entriesRetrieved == 2)
    }

    @Test("startRecovery latest source invokes executor with definition + nil until")
    func startRecoveryLatest() async throws {
        let api = StasisClientLibTestSupport.MockServerApiEndpointClient()
        let executor = MockOperationExecutor()
        let session = try TestSession.make(api: api, executor: executor)
        let model = RecoverModel(session: session)
        var config = RecoverConfig.initial
        config.definition = UUID()
        config.recoverySource = .latest

        await model.startRecovery(config: config)

        #expect(model.didStartRecovery == true)
        let calls = await executor.calls
        if case let .recoveryDefinition(_, until, _, _) = calls.first {
            #expect(until == nil)
        } else {
            Issue.record("expected recoveryDefinition")
        }
    }

    @Test("startRecovery until source forwards the date")
    func startRecoveryUntil() async throws {
        let api = StasisClientLibTestSupport.MockServerApiEndpointClient()
        let executor = MockOperationExecutor()
        let session = try TestSession.make(api: api, executor: executor)
        let model = RecoverModel(session: session)
        let date = Date(timeIntervalSince1970: 12_345)
        var config = RecoverConfig.initial
        config.definition = UUID()
        config.recoverySource = .until(date)

        await model.startRecovery(config: config)

        let calls = await executor.calls
        if case let .recoveryDefinition(_, until, _, _) = calls.first {
            #expect(until == date)
        } else {
            Issue.record("expected recoveryDefinition")
        }
    }

    @Test("startRecovery entry source invokes executor with the entry id")
    func startRecoveryEntry() async throws {
        let api = StasisClientLibTestSupport.MockServerApiEndpointClient()
        let executor = MockOperationExecutor()
        let session = try TestSession.make(api: api, executor: executor)
        let model = RecoverModel(session: session)
        let entryId = UUID()
        var config = RecoverConfig.initial
        config.definition = UUID()
        config.recoverySource = .entry(entryId)

        await model.startRecovery(config: config)

        let calls = await executor.calls
        if case let .recoveryEntry(callEntry, _, _) = calls.first {
            #expect(callEntry == entryId)
        } else {
            Issue.record("expected recoveryEntry")
        }
    }

    @Test("startRecovery forwards a non-empty path query and destination")
    func startRecoveryForwardsExtras() async throws {
        let api = StasisClientLibTestSupport.MockServerApiEndpointClient()
        let executor = MockOperationExecutor()
        let session = try TestSession.make(api: api, executor: executor)
        let model = RecoverModel(session: session)
        var config = RecoverConfig.initial
        config.definition = UUID()
        config.recoverySource = .latest
        config.pathQuery = ".*\\.txt"
        config.destination = "/tmp/dest"

        await model.startRecovery(config: config)

        let calls = await executor.calls
        if case let .recoveryDefinition(_, _, hasQuery, hasDestination) = calls.first {
            #expect(hasQuery == true)
            #expect(hasDestination == true)
        } else {
            Issue.record("expected recoveryDefinition")
        }
    }

    @Test("startRecovery does not set didStartRecovery for an invalid config")
    func startRecoveryInvalidDoesNotSetFlag() async throws {
        let api = StasisClientLibTestSupport.MockServerApiEndpointClient()
        let executor = MockOperationExecutor()
        let session = try TestSession.make(api: api, executor: executor)
        let model = RecoverModel(session: session)

        await model.startRecovery(config: .initial)

        #expect(model.didStartRecovery == false)
        let calls = await executor.calls
        #expect(calls.isEmpty)
    }

    @Test("load surfaces no error path when datasetDefinitions throws (handled gracefully)")
    func loadHandlesError() async throws {
        let api = StasisClientLibTestSupport.MockServerApiEndpointClient()
        await api.setDatasetDefinitionsFailure(AccessDeniedFailure())
        let session = try TestSession.make(api: api)
        let model = RecoverModel(session: session)

        await model.load()

        #expect(model.error != nil)
        #expect(model.definitions.isEmpty)
        #expect(model.isLoadingDefinitions == false)
    }

    @Test("clearError resets the error to nil")
    func clearErrorResets() async throws {
        let api = StasisClientLibTestSupport.MockServerApiEndpointClient()
        await api.setDatasetDefinitionsFailure(AccessDeniedFailure())
        let session = try TestSession.make(api: api)
        let model = RecoverModel(session: session)
        await model.load()
        #expect(model.error != nil)

        model.clearError()

        #expect(model.error == nil)
    }
}
