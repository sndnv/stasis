import Foundation
@testable import StasisClientLib
import Testing

@Suite("Deflate")
struct DeflateTests {
    private let plaintext = "some-decompressed-data"

    @Test("provides its name")
    func name() {
        #expect(Deflate.shared.name == "deflate")
    }

    @Test("compresses and decompresses data")
    func roundTrip() throws {
        let original = Data(plaintext.utf8)
        let compressed = try Deflate.shared.compress(original)
        #expect(compressed != original)
        let decompressed = try Deflate.shared.decompress(compressed)
        #expect(decompressed == original)
    }

    @Test("produces a zlib-framed payload")
    func zlibFraming() throws {
        let compressed = try Deflate.shared.compress(Data(plaintext.utf8))
        #expect(compressed.first == 0x78)
    }

    @Test("decompresses a zlib-framed payload from an external source")
    func decompressExternal() throws {
        let compressed = Data(base64Encoded: "eNoqzs9N1U1JTc7PLShKLS5OTdFNSSxJBAAAAP//AwBkTwin")!
        let decompressed = try Deflate.shared.decompress(compressed)
        #expect(String(bytes: decompressed, encoding: .utf8) == plaintext)
    }

    @Test("round-trips empty input")
    func emptyRoundTrip() throws {
        let compressed = try Deflate.shared.compress(Data())
        #expect(!compressed.isEmpty)
        let decompressed = try Deflate.shared.decompress(compressed)
        #expect(decompressed == Data())
    }

    @Test("round-trips input larger than the internal chunk size")
    func largeRoundTrip() throws {
        let original = Data((0..<1_500_000).map { _ in UInt8.random(in: 0...255) })
        let compressed = try Deflate.shared.compress(original)
        let decompressed = try Deflate.shared.decompress(compressed)
        #expect(decompressed == original)
    }

    @Test("throws on malformed input")
    func decompressMalformed() {
        let garbage = Data([0xFF, 0x00, 0xAB, 0xCD, 0xEF, 0x12, 0x34, 0x56])
        #expect(throws: DeflateError.self) {
            _ = try Deflate.shared.decompress(garbage)
        }
    }

    @Test("accepts input at exactly the 4 GB zlib chunk limit")
    func acceptsInputAtZlibLimit() throws {
        try Deflate.validateInputSize(Int(UInt32.max))
    }

    @Test("rejects input above the 4 GB zlib chunk limit")
    func rejectsInputAboveZlibLimit() {
        #expect(throws: DeflateError.self) {
            try Deflate.validateInputSize(Int(UInt32.max) + 1)
        }
    }
}
