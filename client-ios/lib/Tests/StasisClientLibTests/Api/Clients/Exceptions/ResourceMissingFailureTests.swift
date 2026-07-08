import Foundation
@testable import StasisClientLib
import Testing

@Suite("ResourceMissingFailure")
struct ResourceMissingFailureTests {
    @Test("provides a descriptive message")
    func rendersMessage() {
        #expect(ResourceMissingFailure().errorDescription == "The requested resource was not found")
    }
}
