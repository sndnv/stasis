import Foundation
@testable import StasisClient
import StasisClientLib
import StasisClientLibTestSupport
import Testing

@MainActor
@Suite("EntryDetailModel")
struct EntryDetailModelTests {
    @Test("load populates metadata")
    func loadPopulatesMetadata() async throws {
        let entry = TestGenerators.entry()
        let api = StasisClientLibTestSupport.MockServerApiEndpointClient()
        let customMetadata = DatasetMetadata(
            contentChanged: [:], metadataChanged: [:],
            filesystem: FilesystemMetadata(entities: ["/tmp/foo": .new])
        )
        await api.setDatasetMetadataOverride(entry.id, customMetadata)
        let session = try TestSession.make(api: api)
        let model = EntryDetailModel(session: session, entry: entry)

        await model.load()

        #expect(model.isLoading == false)
        #expect(model.metadata?.filesystem.get("/tmp/foo") == .new)
        #expect(model.error == nil)
    }

    @Test("load surfaces an error when the API throws")
    func loadSurfacesError() async throws {
        let api = StasisClientLibTestSupport.MockServerApiEndpointClient()
        await api.setDatasetMetadataFailure(AccessDeniedFailure())
        let session = try TestSession.make(api: api)
        let model = EntryDetailModel(session: session, entry: TestGenerators.entry())

        await model.load()

        #expect(model.metadata == nil)
        #expect(model.error != nil)
        #expect(model.isLoading == false)
    }

    @Test("clearError resets the error to nil")
    func clearErrorResets() async throws {
        let api = StasisClientLibTestSupport.MockServerApiEndpointClient()
        await api.setDatasetMetadataFailure(AccessDeniedFailure())
        let session = try TestSession.make(api: api)
        let model = EntryDetailModel(session: session, entry: TestGenerators.entry())
        await model.load()
        #expect(model.error != nil)

        model.clearError()

        #expect(model.error == nil)
    }
}
