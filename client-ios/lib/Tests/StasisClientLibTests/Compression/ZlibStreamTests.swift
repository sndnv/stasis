import Foundation
@testable import StasisClientLib
import Testing

@Suite("ZlibStream streaming compression")
struct ZlibStreamTests {
    private func makeOriginal() -> Data {
        var data = Data()
        for index in 0..<20_000 { data.append(Data("test a \(index % 7) ".utf8)) }
        return data
    }

    private func chunks(_ data: Data, size: Int) -> [Data] {
        stride(from: 0, to: data.count, by: size).map { start in
            data.subdata(in: start..<min(start + size, data.count))
        }
    }

    @Test("gzip streaming encode then decode round-trips multi-chunk input")
    func gzipRoundTrips() async throws {
        let original = makeOriginal()
        let source = makeDataStream(chunks(original, size: 4_096))

        let compressed = try await collectData(Gzip.shared.encode(source))
        #expect(compressed.count < original.count)

        let decompressed = try await collectData(Gzip.shared.decode(makeDataStream(compressed)))
        #expect(decompressed == original)
    }

    @Test("deflate streaming encode then decode round-trips multi-chunk input")
    func deflateRoundTrips() async throws {
        let original = makeOriginal()
        let source = makeDataStream(chunks(original, size: 4_096))

        let compressed = try await collectData(Deflate.shared.encode(source))
        let decompressed = try await collectData(Deflate.shared.decode(makeDataStream(compressed)))
        #expect(decompressed == original)
    }

    @Test("streaming gzip output decodes via whole-buffer gunzip and vice versa")
    func gzipInteropsWithWholeBuffer() async throws {
        let original = makeOriginal()

        let streamed = try await collectData(Gzip.shared.encode(makeDataStream(chunks(original, size: 4_096))))
        #expect(try Gzip.shared.decompress(streamed) == original)

        let wholeBuffer = try Gzip.shared.compress(original)
        let decoded = try await collectData(Gzip.shared.decode(makeDataStream(wholeBuffer)))
        #expect(decoded == original)
    }

    @Test("streaming deflate output decodes via whole-buffer inflate and vice versa")
    func deflateInteropsWithWholeBuffer() async throws {
        let original = makeOriginal()

        let streamed = try await collectData(Deflate.shared.encode(makeDataStream(chunks(original, size: 4_096))))
        #expect(try Deflate.shared.decompress(streamed) == original)

        let wholeBuffer = try Deflate.shared.compress(original)
        let decoded = try await collectData(Deflate.shared.decode(makeDataStream(wholeBuffer)))
        #expect(decoded == original)
    }

    @Test("identity streaming passes chunks through unchanged")
    func identityPassThrough() async throws {
        let original = makeOriginal()
        let encoded = try await collectData(Identity.shared.encode(makeDataStream(chunks(original, size: 4_096))))
        #expect(encoded == original)
        let decoded = try await collectData(Identity.shared.decode(makeDataStream(original)))
        #expect(decoded == original)
    }

    @Test("gzip streaming round-trips empty input")
    func gzipRoundTripsEmpty() async throws {
        let compressed = try await collectData(Gzip.shared.encode(makeDataStream([])))
        let decompressed = try await collectData(Gzip.shared.decode(makeDataStream(compressed)))
        #expect(decompressed.isEmpty)
    }
}
