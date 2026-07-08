import Foundation
@testable import StasisClientLib
import Testing

@Suite("OperationRestrictedFailure")
struct OperationRestrictedFailureTests {
    @Test("summarizes the restrictions in the message")
    func rendersMessage() {
        let failure = OperationRestrictedFailure(restrictions: [.noConnection, .limitedNetwork])
        #expect(
            failure.errorDescription
                == "Operation restricted: no network connection, restricted or metered network"
        )
    }
}
