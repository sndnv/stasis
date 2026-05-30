import Foundation
@testable import StasisClientLib
import Testing

@Suite("Identity")
struct IdentityTests {
    @Test("provides its name")
    func name() {
        #expect(Identity.shared.name == "none")
    }

    @Test("skips compression")
    func compress() throws {
        let data = Data("some-data".utf8)
        let compressed = try Identity.shared.compress(data)
        #expect(compressed == data)
    }

    @Test("skips decompression")
    func decompress() throws {
        let data = Data("some-data".utf8)
        let decompressed = try Identity.shared.decompress(data)
        #expect(decompressed == data)
    }
}
