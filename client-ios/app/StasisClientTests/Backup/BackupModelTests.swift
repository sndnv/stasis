import Foundation
@testable import StasisClient
import StasisClientLib
import StasisClientLibTestSupport
import Testing

@MainActor
@Suite("BackupModel")
struct BackupModelTests {
    @Test("load populates definitions sorted by created ascending")
    func loadSortsByCreatedAscending() async throws {
        let api = StasisClientLibTestSupport.MockServerApiEndpointClient()
        let newer = makeDefinition(info: "newer", created: Date(timeIntervalSince1970: 200))
        let older = makeDefinition(info: "older", created: Date(timeIntervalSince1970: 100))
        await api.setDatasetDefinitionsOverride([newer, older])
        let session = try TestSession.make(api: api)
        let model = BackupModel(session: session)

        await model.load()

        #expect(model.isLoading == false)
        #expect(model.definitions.map(\.id) == [older.id, newer.id])
        #expect(model.defaultDefinitionId == older.id)
        #expect(model.error == nil)
    }

    private func makeDefinition(info: String, created: Date) -> DatasetDefinition {
        DatasetDefinition(
            id: UUID(), info: info, device: UUID(), redundantCopies: 1,
            existingVersions: .init(policy: .all, duration: SecondsDuration(3600)),
            removedVersions: .init(policy: .all, duration: SecondsDuration(3600)),
            created: created, updated: created
        )
    }

    @Test("defaultDefinitionId is nil when no definitions exist")
    func defaultDefinitionIdNilWhenEmpty() async throws {
        let api = StasisClientLibTestSupport.MockServerApiEndpointClient()
        await api.setDatasetDefinitionsOverride([])
        let session = try TestSession.make(api: api)
        let model = BackupModel(session: session)

        await model.load()

        #expect(model.definitions.isEmpty)
        #expect(model.defaultDefinitionId == nil)
    }

    @Test("load surfaces error and clears definitions on failure")
    func loadSurfacesError() async throws {
        let api = StasisClientLibTestSupport.MockServerApiEndpointClient()
        await api.setDatasetDefinitionsFailure(AccessDeniedFailure())
        let session = try TestSession.make(api: api)
        let model = BackupModel(session: session)

        await model.load()

        #expect(model.definitions.isEmpty)
        #expect(model.error != nil)
        #expect(model.isLoading == false)
    }

    @Test("createDefinition reloads on success")
    func createDefinitionReloads() async throws {
        let api = StasisClientLibTestSupport.MockServerApiEndpointClient()
        let session = try TestSession.make(api: api)
        let model = BackupModel(session: session)

        let request = CreateDatasetDefinition(
            info: "test", device: UUID(), redundantCopies: 1,
            existingVersions: .init(policy: .all, duration: SecondsDuration(3600)),
            removedVersions: .init(policy: .all, duration: SecondsDuration(3600))
        )
        let success = await model.createDefinition(request)

        #expect(success == true)
        let calls = await api.calls
        #expect(calls.definitionCreated == 1)
        #expect(calls.definitionsRetrieved == 1)
    }

    @Test("updateDefinition reloads on success")
    func updateDefinitionReloads() async throws {
        let api = StasisClientLibTestSupport.MockServerApiEndpointClient()
        let session = try TestSession.make(api: api)
        let model = BackupModel(session: session)

        let request = UpdateDatasetDefinition(
            info: "updated", redundantCopies: 2,
            existingVersions: .init(policy: .all, duration: SecondsDuration(3600)),
            removedVersions: .init(policy: .all, duration: SecondsDuration(3600))
        )
        let success = await model.updateDefinition(UUID(), with: request)

        #expect(success == true)
        let calls = await api.calls
        #expect(calls.definitionUpdated == 1)
        #expect(calls.definitionsRetrieved == 1)
    }

    @Test("deleteDefinition reloads definitions")
    func deleteDefinitionReloads() async throws {
        let api = StasisClientLibTestSupport.MockServerApiEndpointClient()
        let session = try TestSession.make(api: api)
        let model = BackupModel(session: session)

        await model.deleteDefinition(UUID())

        let calls = await api.calls
        #expect(calls.definitionDeleted == 1)
        #expect(calls.definitionsRetrieved == 1)
    }

    @Test("clearError resets the error to nil")
    func clearErrorResets() async throws {
        let api = StasisClientLibTestSupport.MockServerApiEndpointClient()
        await api.setDatasetDefinitionsFailure(AccessDeniedFailure())
        let session = try TestSession.make(api: api)
        let model = BackupModel(session: session)
        await model.load()
        #expect(model.error != nil)

        model.clearError()

        #expect(model.error == nil)
    }

    @Test("selfDevice comes from the server API client")
    func selfDeviceFromApi() async throws {
        let device = UUID()
        let api = StasisClientLibTestSupport.MockServerApiEndpointClient(selfDevice: device)
        let session = try TestSession.make(api: api)
        let model = BackupModel(session: session)

        #expect(model.selfDevice == device)
    }
}
