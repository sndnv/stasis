import Foundation
@testable import StasisClientLib
import Testing

@Suite("Clients")
struct ClientsTests {
    private struct TestFailure: Error, Equatable {}

    @Test("provides static clients")
    func providesStaticClients() async throws {
        let api = MockServerApiEndpointClient()
        let core = MockServerCoreEndpointClient()

        let clients = ClientsFactory.make(api: api, core: core)
        #expect(clients is StaticClients)

        #expect(identityEqual(try await clients.api(), api))
        #expect(identityEqual(try await clients.core(), core))

        await clients.withDiscovery(.failure(TestFailure()))

        #expect(identityEqual(try await clients.api(), api))
        #expect(identityEqual(try await clients.core(), core))
    }

    @Test("provides discovery-based clients")
    func providesDiscoveredClients() async throws {
        let clients = ClientsFactory.discovered()
        #expect(clients is DiscoveredClients)

        await #expect(throws: DiscoveryFailure.self) { _ = try await clients.api() }
        await #expect(throws: DiscoveryFailure.self) { _ = try await clients.core() }

        await clients.withDiscovery(.failure(TestFailure()))
        await #expect(throws: TestFailure.self) { _ = try await clients.api() }
        await #expect(throws: TestFailure.self) { _ = try await clients.core() }

        let discoveredApi = MockServerApiEndpointClient()
        let discoveredCore = MockServerCoreEndpointClient()
        let provider = DisabledServiceDiscoveryProvider(initialClients: [discoveredApi, discoveredCore])
        await clients.withDiscovery(.success(provider))

        #expect(identityEqual(try await clients.api(), discoveredApi))
        #expect(identityEqual(try await clients.core(), discoveredCore))
    }

    private func identityEqual(_ lhs: Any, _ rhs: Any) -> Bool {
        (lhs as AnyObject) === (rhs as AnyObject)
    }
}
