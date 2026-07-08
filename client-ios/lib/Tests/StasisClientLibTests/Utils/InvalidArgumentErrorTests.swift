import Foundation
@testable import StasisClientLib
import Testing

@Suite("InvalidArgumentError")
struct InvalidArgumentErrorTests {
    @Test("surfaces the provided message")
    func rendersMessage() {
        #expect(InvalidArgumentError("test a").errorDescription == "test a")
    }
}
