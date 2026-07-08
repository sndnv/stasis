import Foundation
@testable import StasisClientLib
import Testing

@Suite("ExplicitLogout")
struct ExplicitLogoutTests {
    @Test("provides a descriptive message")
    func rendersMessage() {
        #expect(ExplicitLogout().errorDescription == "The session was logged out")
    }
}
