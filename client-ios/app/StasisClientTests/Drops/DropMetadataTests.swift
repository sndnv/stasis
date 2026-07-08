import Foundation
@testable import StasisClient
import Testing

@Suite("DropMetadata")
struct DropMetadataTests {
    @Test("round-trips through its encoded form")
    func roundTrips() throws {
        let metadata = DropMetadata(
            id: "2380B7C6-9C31-4E2F-9C4C-1F9B0A6C9E10",
            filename: "test.txt",
            size: 6,
            typeIdentifier: "public.plain-text",
            createdAt: Date(timeIntervalSince1970: 1000)
        )

        let decoded = try #require(DropMetadata.decoded(from: try metadata.encoded()))
        #expect(decoded == metadata)
    }

    @Test("decodes to nil for malformed data")
    func decodesNilForGarbage() {
        #expect(DropMetadata.decoded(from: Data("test".utf8)) == nil)
    }
}
