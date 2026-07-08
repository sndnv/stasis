import Foundation
@testable import StasisClientLib
import Testing

@Suite("MissingDeviceSecret")
struct MissingDeviceSecretTests {
    @Test("provides a descriptive message")
    func rendersMessage() {
        #expect(MissingDeviceSecret().errorDescription == "No device secret is available")
    }
}
