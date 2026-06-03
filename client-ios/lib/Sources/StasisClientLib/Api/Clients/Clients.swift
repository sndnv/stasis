import Foundation
import Synchronization

public protocol Clients: Sendable {
    func api() async throws -> any ServerApiEndpointClient
    func core() async throws -> any ServerCoreEndpointClient

    func withDiscovery(_ discovery: Result<any ServiceDiscoveryProvider, Error>) async
}

public struct StaticClients: Clients {
    private let staticApi: any ServerApiEndpointClient
    private let staticCore: any ServerCoreEndpointClient

    public init(api: any ServerApiEndpointClient, core: any ServerCoreEndpointClient) {
        self.staticApi = api
        self.staticCore = core
    }

    public func api() async throws -> any ServerApiEndpointClient { staticApi }
    public func core() async throws -> any ServerCoreEndpointClient { staticCore }
    public func withDiscovery(_ discovery: Result<any ServiceDiscoveryProvider, Error>) async {}
}

public actor DiscoveredClients: Clients {
    private var discovery: Result<any ServiceDiscoveryProvider, Error>

    public init() {
        self.discovery = .failure(DiscoveryFailure(message: "No discovery provider found"))
    }

    public func api() async throws -> any ServerApiEndpointClient {
        try await discovery.get().latest(ServerApiEndpointClient.self)
    }

    public func core() async throws -> any ServerCoreEndpointClient {
        try await discovery.get().latest(ServerCoreEndpointClient.self)
    }

    public func withDiscovery(_ discovery: Result<any ServiceDiscoveryProvider, Error>) {
        self.discovery = discovery
    }
}

public enum ClientsFactory {
    public static func make(api: any ServerApiEndpointClient, core: any ServerCoreEndpointClient) -> any Clients {
        StaticClients(api: api, core: core)
    }

    public static func discovered() -> any Clients {
        DiscoveredClients()
    }
}
