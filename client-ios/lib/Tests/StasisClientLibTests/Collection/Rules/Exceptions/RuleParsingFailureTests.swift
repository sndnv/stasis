import Foundation
@testable import StasisClientLib
import Testing

@Suite("RuleParsingFailure")
struct RuleParsingFailureTests {
    @Test("surfaces the provided message")
    func rendersMessage() {
        #expect(RuleParsingFailure("test a").errorDescription == "test a")
    }
}
