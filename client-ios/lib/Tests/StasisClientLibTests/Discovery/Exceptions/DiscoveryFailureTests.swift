import Foundation
@testable import StasisClientLib
import Testing

@Suite("DiscoveryFailure")
struct DiscoveryFailureTests {
    @Test("surfaces the provided message")
    func rendersMessage() {
        #expect(DiscoveryFailure(message: "test a").errorDescription == "test a")
    }
}
