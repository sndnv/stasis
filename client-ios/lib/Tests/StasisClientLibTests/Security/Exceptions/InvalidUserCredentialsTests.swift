import Foundation
@testable import StasisClientLib
import Testing

@Suite("InvalidUserCredentials")
struct InvalidUserCredentialsTests {
    @Test("provides a descriptive message")
    func rendersMessage() {
        #expect(InvalidUserCredentials().errorDescription == "Invalid credentials provided")
    }
}
