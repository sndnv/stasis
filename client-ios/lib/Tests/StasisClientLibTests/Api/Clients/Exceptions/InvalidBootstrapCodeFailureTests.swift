import Foundation
@testable import StasisClientLib
import Testing

@Suite("InvalidBootstrapCodeFailure")
struct InvalidBootstrapCodeFailureTests {
    @Test("provides a descriptive message")
    func rendersMessage() {
        #expect(InvalidBootstrapCodeFailure().errorDescription == "The provided bootstrap code is invalid")
    }
}
