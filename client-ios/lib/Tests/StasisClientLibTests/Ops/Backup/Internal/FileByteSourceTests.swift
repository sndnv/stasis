import Foundation
@testable import StasisClientLib
import Testing

@Suite("FileByteSource")
struct FileByteSourceTests {
    private func writeTemporaryFile(_ data: Data) throws -> URL {
        let directory = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString)
        try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
        let file = directory.appendingPathComponent("file")
        try data.write(to: file)
        return file
    }

    @Test("streams a file whose size is not a multiple of the chunk size")
    func streamsUnevenFile() async throws {
        let original = Data((0..<2_500).map { UInt8($0 % 251) })
        let file = try writeTemporaryFile(original)

        var chunks: [Data] = []
        for try await chunk in FileByteSource.read(file, chunkSize: 1_000) { chunks.append(chunk) }

        #expect(chunks.map(\.count) == [1_000, 1_000, 500])
        #expect(chunks.reduce(Data(), +) == original)
    }

    @Test("streams a file whose size is an exact multiple of the chunk size")
    func streamsExactMultiple() async throws {
        let original = Data((0..<2_000).map { UInt8($0 % 251) })
        let file = try writeTemporaryFile(original)

        let collected = try await collectData(FileByteSource.read(file, chunkSize: 1_000))
        #expect(collected == original)
    }

    @Test("yields nothing for an empty file")
    func streamsEmptyFile() async throws {
        let file = try writeTemporaryFile(Data())
        let collected = try await collectData(FileByteSource.read(file, chunkSize: 1_000))
        #expect(collected.isEmpty)
    }

    @Test("surfaces an error for a missing file")
    func failsForMissingFile() async {
        let missing = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString)
        await #expect(throws: (any Error).self) {
            _ = try await collectData(FileByteSource.read(missing, chunkSize: 1_000))
        }
    }
}
