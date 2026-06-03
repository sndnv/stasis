import Foundation
@testable import StasisClientLib
import Testing

@Suite("DecompressedSource")
struct DecompressedSourceTests {
    @Test("decompresses a data stream via the provided decoder")
    func decompressesStream() async throws {
        let decoder = StubDecoder(result: .success(Data("decompressed".utf8)))
        let source = makeDataStream(Data("original".utf8))

        let decompressed = DecompressedSource.decompress(source, decompressor: decoder)
        var collected = Data()
        for try await chunk in decompressed { collected.append(chunk) }

        #expect(String(data: collected, encoding: .utf8) == "decompressed")
    }

    @Test("propagates errors raised by the input stream")
    func propagatesSourceFailures() async {
        let decoder = StubDecoder(result: .success(Data()))
        let source = makeDataStream([Data("partial".utf8)], thenThrow: TestFailure(message: "stream broken"))

        let decompressed = DecompressedSource.decompress(source, decompressor: decoder)
        await #expect(throws: TestFailure(message: "stream broken")) {
            for try await _ in decompressed {}
        }
    }

    @Test("propagates errors raised by the decompressor")
    func propagatesDecoderFailures() async {
        let decoder = StubDecoder(result: .failure(TestFailure(message: "decompress failed")))
        let source = makeDataStream(Data("original".utf8))

        let decompressed = DecompressedSource.decompress(source, decompressor: decoder)
        await #expect(throws: TestFailure(message: "decompress failed")) {
            for try await _ in decompressed {}
        }
    }
}

private struct StubDecoder: CompressionDecoder {
    let result: Result<Data, any Error>
    var name: String { "stub" }
    func decompress(_: Data) throws -> Data { try result.get() }
}
