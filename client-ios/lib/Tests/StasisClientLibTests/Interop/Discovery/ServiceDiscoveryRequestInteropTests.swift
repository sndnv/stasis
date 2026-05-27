@testable import StasisClientLib
import Testing

@Suite("ServiceDiscoveryRequest interop")
struct ServiceDiscoveryRequestInteropTests {
    @Test("decode and re-encode a request")
    func request() throws {
        try assert(
            domain: "discovery",
            resource: "ServiceDiscoveryRequest",
            matches: ServiceDiscoveryRequest(
                isInitialRequest: false,
                attributes: [
                    "device": "1a68637d-e3cd-47d0-b683-ddf99b064056",
                    "node": "2df1bd32-0dfe-4a6e-9981-589c7180206b",
                    "user": "cef57a23-22c1-4b87-82e4-56ee622ead27"
                ]
            )
        )
    }
}
