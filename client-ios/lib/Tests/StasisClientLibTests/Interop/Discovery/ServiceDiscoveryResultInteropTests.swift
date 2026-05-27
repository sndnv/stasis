@testable import StasisClientLib
import Testing

@Suite("ServiceDiscoveryResult interop")
struct ServiceDiscoveryResultInteropTests {
    @Test("decode and re-encode keepExisting")
    func keepExisting() throws {
        try assert(
            domain: "discovery",
            resource: "ServiceDiscoveryResult.keep-existing",
            matches: ServiceDiscoveryResult.keepExisting
        )
    }

    @Test("decode and re-encode switchTo with an HTTP core endpoint")
    func switchToHttp() throws {
        try assert(
            domain: "discovery",
            resource: "ServiceDiscoveryResult.switch-to-http",
            matches: ServiceDiscoveryResult.switchTo(
                endpoints: ServiceDiscoveryResult.Endpoints(
                    api: ServiceApiEndpoint.Api(uri: "https://api.example.test"),
                    core: ServiceApiEndpoint.Core(address: .http(uri: "https://core.example.test")),
                    discovery: ServiceApiEndpoint.Discovery(uri: "https://discovery.example.test")
                ),
                recreateExisting: false
            )
        )
    }

    @Test("decode and re-encode switchTo with a gRPC core endpoint")
    func switchToGrpc() throws {
        try assert(
            domain: "discovery",
            resource: "ServiceDiscoveryResult.switch-to-grpc",
            matches: ServiceDiscoveryResult.switchTo(
                endpoints: ServiceDiscoveryResult.Endpoints(
                    api: ServiceApiEndpoint.Api(uri: "https://api.example.test"),
                    core: ServiceApiEndpoint.Core(
                        address: .grpc(host: "core.example.test", port: 9999, tlsEnabled: true)
                    ),
                    discovery: ServiceApiEndpoint.Discovery(uri: "https://discovery.example.test")
                ),
                recreateExisting: true
            )
        )
    }
}
