import Foundation
@testable import StasisClient
import Testing

@Suite("RuleEntityError")
struct RuleEntityErrorTests {
    @Test("describes an unknown operation")
    func message() {
        #expect(RuleEntityError.unknownOperation("test").errorDescription == "Unknown rule operation [test]")
    }
}
