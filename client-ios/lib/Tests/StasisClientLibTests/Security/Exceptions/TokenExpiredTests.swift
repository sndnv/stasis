import Foundation
@testable import StasisClientLib
import Testing

@Suite("TokenExpired")
struct TokenExpiredTests {
    @Test("provides a descriptive message")
    func rendersMessage() {
        #expect(TokenExpired().errorDescription == "The authentication token has expired")
    }
}
