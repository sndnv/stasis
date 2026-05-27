public protocol ServiceDiscoveryClient: ServiceApiClient {
    var attributes: any ServiceDiscoveryClientAttributes { get }

    func latest(isInitialRequest: Bool) async throws -> ServiceDiscoveryResult
}

public protocol ServiceDiscoveryClientAttributes: Sendable {
    func asServiceDiscoveryRequest(isInitialRequest: Bool) -> ServiceDiscoveryRequest
}
