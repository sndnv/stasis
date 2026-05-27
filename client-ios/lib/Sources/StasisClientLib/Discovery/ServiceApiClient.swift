public protocol ServiceApiClient: Sendable {}

public protocol ServiceApiClientFactory: Sendable {
    func create(endpoint: ServiceApiEndpoint.Api, coreClient: any ServiceApiClient) -> any ServiceApiClient
    func create(endpoint: ServiceApiEndpoint.Core) -> any ServiceApiClient
    func create(endpoint: ServiceApiEndpoint.Discovery) -> any ServiceApiClient
}
