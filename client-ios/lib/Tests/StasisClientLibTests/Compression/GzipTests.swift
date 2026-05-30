import Foundation
@testable import StasisClientLib
import Testing

@Suite("Gzip")
struct GzipTests {
    private let plaintext = "some-decompressed-data"

    @Test("provides its name")
    func name() {
        #expect(Gzip.shared.name == "gzip")
    }

    @Test("compresses and decompresses data")
    func roundTrip() throws {
        let original = Data(plaintext.utf8)
        let compressed = try Gzip.shared.compress(original)
        #expect(compressed != original)
        let decompressed = try Gzip.shared.decompress(compressed)
        #expect(decompressed == original)
    }

    @Test("produces a gzip-framed payload that other clients can decode")
    func gzipFraming() throws {
        let compressed = try Gzip.shared.compress(Data(plaintext.utf8))
        #expect(compressed.prefix(2) == Data([0x1F, 0x8B]))
    }

    @Test("decompresses a gzip-framed payload from an external source")
    func decompressExternal() throws {
        let compressed = Data(base64Encoded: "H4sIAAAAAAAAACrOz03VTUlNzs8tKEotLk5N0U1JLEkEAAAA//8DAG894xUWAAAA")!
        let decompressed = try Gzip.shared.decompress(compressed)
        #expect(String(bytes: decompressed, encoding: .utf8) == plaintext)
    }
}
