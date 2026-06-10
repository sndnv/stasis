import Foundation
@testable import StasisClient
import StasisClientLib
import StasisClientLibTestSupport
import Testing

@MainActor
@Suite("StatusModel")
struct StatusModelTests {
    @Test("load populates user, device, and snapshots servers")
    func loadPopulates() async throws {
        let api = StasisClientLibTestSupport.MockServerApiEndpointClient()
        let session = try TestSession.make(api: api)
        let trackers = try TestDefaults.trackers()
        await trackers.server.reachable(server: "https://api.test")
        let model = StatusModel(session: session, trackers: trackers)

        await model.load()

        #expect(model.isLoading == false)
        #expect(model.user != nil)
        #expect(model.device != nil)
        #expect(model.servers["https://api.test"]?.reachable == true)
        let calls = await api.calls
        #expect(calls.userRetrieved == 1)
        #expect(calls.deviceRetrieved == 1)
    }

    @Test("load surfaces an error message when the API throws")
    func loadSurfacesError() async throws {
        let api = StasisClientLibTestSupport.MockServerApiEndpointClient()
        await api.setUserFailure(AccessDeniedFailure())
        let session = try TestSession.make(api: api)
        let model = StatusModel(session: session, trackers: try TestDefaults.trackers())

        await model.load()

        #expect(model.isLoading == false)
        #expect(model.error != nil)
        #expect(model.user == nil)
    }

    @Test("clearError resets the error to nil")
    func clearErrorResets() async throws {
        let api = StasisClientLibTestSupport.MockServerApiEndpointClient()
        await api.setUserFailure(AccessDeniedFailure())
        let session = try TestSession.make(api: api)
        let model = StatusModel(session: session, trackers: try TestDefaults.trackers())
        await model.load()
        #expect(model.error != nil)

        model.clearError()

        #expect(model.error == nil)
    }
}
