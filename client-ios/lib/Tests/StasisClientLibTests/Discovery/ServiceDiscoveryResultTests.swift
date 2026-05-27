@testable import StasisClientLib
import Testing

@Suite("ServiceDiscoveryResult")
struct ServiceDiscoveryResultTests {
    @Test("renders as a string")
    func resultRendersAsString() {
        #expect(ServiceDiscoveryResult.keepExisting.asString == "result=keep-existing")

        let switchTo = ServiceDiscoveryResult.switchTo(
            endpoints: ServiceDiscoveryResult.Endpoints(
                api: ServiceApiEndpoint.Api(uri: "test-api"),
                core: ServiceApiEndpoint.Core(address: .http(uri: "test-core")),
                discovery: ServiceApiEndpoint.Discovery(uri: "test-discovery")
            ),
            recreateExisting: false
        )

        #expect(
            switchTo.asString
                == "result=switch-to,endpoints=api__test-api;core_http__test-core;discovery__test-discovery,recreate-existing=false"
        )
    }

    @Test("endpoints render as a string")
    func endpointsRenderAsString() {
        let endpoints = ServiceDiscoveryResult.Endpoints(
            api: ServiceApiEndpoint.Api(uri: "test-api"),
            core: ServiceApiEndpoint.Core(address: .http(uri: "test-core")),
            discovery: ServiceApiEndpoint.Discovery(uri: "test-discovery")
        )

        #expect(endpoints.asString == "api__test-api;core_http__test-core;discovery__test-discovery")
    }
}
