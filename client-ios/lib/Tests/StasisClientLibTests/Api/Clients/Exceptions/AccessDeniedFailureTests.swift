import Foundation
@testable import StasisClientLib
import Testing

@Suite("AccessDeniedFailure")
struct AccessDeniedFailureTests {
    @Test("provides a descriptive message")
    func rendersMessage() {
        #expect(AccessDeniedFailure().errorDescription == "Access denied")
    }
}
