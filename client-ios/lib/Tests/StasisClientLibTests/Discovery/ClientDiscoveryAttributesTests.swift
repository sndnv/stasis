import Foundation
@testable import StasisClientLib
import Testing

@Suite("ClientDiscoveryAttributes")
struct ClientDiscoveryAttributesTests {
    @Test("supports providing its attributes as a service discovery request")
    func providesAsServiceDiscoveryRequest() {
        let user = UUID()
        let device = UUID()
        let node = UUID()

        let attributes = ClientDiscoveryAttributes(user: user, device: device, node: node)

        let expected = ServiceDiscoveryRequest(
            isInitialRequest: true,
            attributes: [
                "user": user.uuidString.lowercased(),
                "device": device.uuidString.lowercased(),
                "node": node.uuidString.lowercased()
            ]
        )

        #expect(attributes.asServiceDiscoveryRequest(isInitialRequest: true) == expected)
    }
}
