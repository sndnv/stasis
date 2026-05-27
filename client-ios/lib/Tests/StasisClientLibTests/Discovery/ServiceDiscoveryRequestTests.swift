@testable import StasisClientLib
import Testing

@Suite("ServiceDiscoveryRequest")
struct ServiceDiscoveryRequestTests {
    @Test("supports converting its attributes to a request ID")
    func providesIdFromSortedAttributes() {
        let request = ServiceDiscoveryRequest(
            isInitialRequest: false,
            attributes: [
                "b": "42",
                "c": "false",
                "a": "test-string"
            ]
        )

        #expect(request.id == "a=test-string::b=42::c=false")
    }
}
