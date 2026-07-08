import Foundation
@testable import StasisClientLib
import Testing

@Suite("RuleMatchingFailure")
struct RuleMatchingFailureTests {
    @Test("surfaces the provided message")
    func rendersMessage() {
        #expect(RuleMatchingFailure("test a").errorDescription == "test a")
    }
}
