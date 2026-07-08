import Foundation
@testable import StasisClientLib
import Testing

@Suite("StateSerdesError")
struct StateSerdesErrorTests {
    @Test("describes an invalid operation id")
    func rendersMessage() {
        #expect(StateSerdesError.invalidOperationId("test").errorDescription == "Invalid operation ID [test]")
    }
}
