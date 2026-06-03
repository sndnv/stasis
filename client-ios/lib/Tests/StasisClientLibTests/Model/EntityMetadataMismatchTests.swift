import Foundation
@testable import StasisClientLib
import Testing

@Suite("EntityMetadataMismatch")
struct EntityMetadataMismatchTests {
    @Test("renders a descriptive message")
    func rendersMessage() {
        let mismatch = EntityMetadataMismatch(
            currentPath: "/tmp/current",
            existingPath: "/tmp/existing"
        )

        #expect(mismatch.message == "Mismatched current metadata for [/tmp/current] and existing metadata for [/tmp/existing]")
    }
}
