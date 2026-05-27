@testable import StasisClientLib

final class MockServiceDiscoveryClient: ServiceDiscoveryClient {
    let attributes: any ServiceDiscoveryClientAttributes
    private let initialDiscoveryResult: ServiceDiscoveryResult
    private let nextDiscoveryResult: ServiceDiscoveryResult

    init(
        initialDiscoveryResult: ServiceDiscoveryResult = .keepExisting,
        nextDiscoveryResult: ServiceDiscoveryResult = .keepExisting
    ) {
        self.attributes = TestAttributes(a: "b")
        self.initialDiscoveryResult = initialDiscoveryResult
        self.nextDiscoveryResult = nextDiscoveryResult
    }

    func latest(isInitialRequest: Bool) async throws -> ServiceDiscoveryResult {
        isInitialRequest ? initialDiscoveryResult : nextDiscoveryResult
    }

    struct TestAttributes: ServiceDiscoveryClientAttributes {
        let a: String
        func asServiceDiscoveryRequest(isInitialRequest: Bool) -> ServiceDiscoveryRequest {
            ServiceDiscoveryRequest(isInitialRequest: isInitialRequest, attributes: ["a": a])
        }
    }
}
