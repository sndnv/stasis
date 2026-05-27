import Foundation

public protocol ServerBootstrapEndpointClient: Sendable {
    var server: String { get }

    func execute(bootstrapCode: String) async throws -> DeviceBootstrapParameters
}

public protocol ServerBootstrapEndpointClientFactory: Sendable {
    func create(server: String) -> any ServerBootstrapEndpointClient
}
