import Foundation
@testable import StasisClient
import StasisClientLib
import StasisClientLibTestSupport
import Testing

@MainActor
@Suite("AvailableCommandsModel")
struct AvailableCommandsModelTests {
    @Test("starts in .loading and transitions to .loaded after a successful fetch")
    func loadSucceeds() async throws {
        let api = StasisClientLibTestSupport.MockServerApiEndpointClient()
        let session = try TestSession.make(api: api)
        let preferences = TestDefaults.isolatedDefaults()
        preferences.saveLastProcessedCommand(7)
        let model = AvailableCommandsModel(session: session, preferences: preferences)
        #expect(model.state == .loading)

        await model.load()

        guard case let .loaded(commands, lastProcessed) = model.state else {
            Issue.record("expected .loaded, got \(model.state)")
            return
        }
        let ids = commands.map(\.sequenceId)
        #expect(ids == ids.sorted(by: >))
        #expect(ids == [3, 2, 1])
        #expect(lastProcessed == 7)
    }

    @Test("transitions to .failed when the server rejects the request")
    func loadFails() async throws {
        let api = StasisClientLibTestSupport.MockServerApiEndpointClient(commandsDisabled: true)
        let session = try TestSession.make(api: api)
        let preferences = TestDefaults.isolatedDefaults()
        let model = AvailableCommandsModel(session: session, preferences: preferences)

        await model.load()

        guard case .failed = model.state else {
            Issue.record("expected .failed, got \(model.state)")
            return
        }
    }

    @Test("defaults lastProcessedCommand to 0 when no preference is set")
    func defaultLastProcessed() async throws {
        let api = StasisClientLibTestSupport.MockServerApiEndpointClient()
        let session = try TestSession.make(api: api)
        let preferences = TestDefaults.isolatedDefaults()
        let model = AvailableCommandsModel(session: session, preferences: preferences)

        await model.load()

        guard case let .loaded(_, lastProcessed) = model.state else {
            Issue.record("expected .loaded")
            return
        }
        #expect(lastProcessed == 0)
    }
}
