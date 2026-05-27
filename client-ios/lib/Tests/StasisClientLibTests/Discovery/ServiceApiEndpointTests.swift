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
    }
}
