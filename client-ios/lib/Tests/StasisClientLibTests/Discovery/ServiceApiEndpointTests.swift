@testable import StasisClientLib
import Testing

@Suite("ServiceApiEndpoint")
struct ServiceApiEndpointTests {
    @Test("supports providing an ID")
    func providesId() {
        #expect(ServiceApiEndpoint.Api(uri: "test-uri").id == "api__test-uri")

        #expect(
            ServiceApiEndpoint.Core(address: .http(uri: "test-uri")).id
                == "core_http__test-uri"
        )

        #expect(
            ServiceApiEndpoint.Core(
                address: .grpc(host: "test-host", port: 1234, tlsEnabled: false)
            ).id == "core_grpc__test-host:1234"
        )

        #expect(ServiceApiEndpoint.Discovery(uri: "test-uri").id == "discovery__test-uri")
    }

    @Test("exposes the inner endpoint's ID for each case")
    func providesOuterId() {
        let api = ServiceApiEndpoint.Api(uri: "api-uri")
        let core = ServiceApiEndpoint.Core(address: .http(uri: "core-uri"))
        let discovery = ServiceApiEndpoint.Discovery(uri: "discovery-uri")

        #expect(ServiceApiEndpoint.api(api).id == api.id)
        #expect(ServiceApiEndpoint.core(core).id == core.id)
        #expect(ServiceApiEndpoint.discovery(discovery).id == discovery.id)
    }
}
