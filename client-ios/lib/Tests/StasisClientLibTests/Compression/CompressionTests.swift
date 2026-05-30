import Foundation
@testable import StasisClientLib
import Testing

@Suite("Compression")
struct CompressionTests {
    @Test("provides instances based on config (string)")
    func createFromStrings() throws {
        let compression = try Compressions.create(defaultCompression: "gzip", disabledExtensions: "a, b, c")
        #expect(compression.defaultCompression.name == "gzip")
        #expect(compression.disabledExtensions == ["a", "b", "c"])
    }

    @Test("provides codecs based on name")
    func fromString() throws {
        #expect(try Compressions.fromString("deflate").name == "deflate")
        #expect(try Compressions.fromString("gzip").name == "gzip")
        #expect(try Compressions.fromString("none").name == "none")
    }

    @Test("rejects unsupported codec names")
    func fromStringRejectsUnknown() {
        #expect(throws: CompressionError.unsupported("other")) {
            try Compressions.fromString("other")
        }
    }

    @Test("determines compression algorithm based on entity path")
    func algorithmFor() throws {
        let compression = try Compressions.create(defaultCompression: "gzip", disabledExtensions: "ext1,ext2,ext3")
        #expect(compression.algorithmFor(entity: URL(fileURLWithPath: "/tmp/file1")) == "gzip")
        #expect(compression.algorithmFor(entity: URL(fileURLWithPath: "/tmp/file1.ext")) == "gzip")
        #expect(compression.algorithmFor(entity: URL(fileURLWithPath: "/tmp/file1.ext1.ext")) == "gzip")
        #expect(compression.algorithmFor(entity: URL(fileURLWithPath: "/tmp/file1.ext1")) == "none")
        #expect(compression.algorithmFor(entity: URL(fileURLWithPath: "/tmp/file1.ext2")) == "none")
        #expect(compression.algorithmFor(entity: URL(fileURLWithPath: "/tmp/file1.ext3")) == "none")
        #expect(compression.algorithmFor(entity: URL(fileURLWithPath: "/tmp/file1ext1")) == "gzip")
        #expect(compression.algorithmFor(entity: URL(fileURLWithPath: "/tmp/file1ext2")) == "gzip")
        #expect(compression.algorithmFor(entity: URL(fileURLWithPath: "/tmp/file1ext3")) == "gzip")
    }

    @Test("provides encoders for source entities")
    func encoderFor() throws {
        let compression = try Compressions.create(defaultCompression: "deflate", disabledExtensions: "a,b,c")
        let fileOne = try SourceEntity(
            path: URL(fileURLWithPath: "/tmp/a"),
            existingMetadata: nil,
            currentMetadata: Fixtures.Metadata.fileOne
        )
        let fileTwo = try SourceEntity(
            path: URL(fileURLWithPath: "/tmp/a"),
            existingMetadata: nil,
            currentMetadata: Fixtures.Metadata.fileTwo
        )
        let fileThree = try SourceEntity(
            path: URL(fileURLWithPath: "/tmp/a"),
            existingMetadata: nil,
            currentMetadata: Fixtures.Metadata.fileThree
        )
        #expect(try compression.encoderFor(entity: fileOne).name == "none")
        #expect(try compression.encoderFor(entity: fileTwo).name == "gzip")
        #expect(try compression.encoderFor(entity: fileThree).name == "deflate")
    }

    @Test("fails to provide encoders for directories")
    func encoderForRejectsDirectory() throws {
        let compression = try Compressions.create(defaultCompression: "deflate", disabledExtensions: "a,b,c")
        let directory = try SourceEntity(
            path: URL(fileURLWithPath: "/tmp/a"),
            existingMetadata: nil,
            currentMetadata: Fixtures.Metadata.directoryOne
        )
        #expect(throws: CompressionError.self) {
            _ = try compression.encoderFor(entity: directory)
        }
    }

    @Test("provides decoders for target entities")
    func decoderFor() throws {
        let compression = try Compressions.create(defaultCompression: "deflate", disabledExtensions: "a,b,c")
        let fileOne = try TargetEntity(
            path: URL(fileURLWithPath: "/tmp/a"),
            destination: .default,
            existingMetadata: Fixtures.Metadata.fileOne,
            currentMetadata: nil
        )
        let fileTwo = try TargetEntity(
            path: URL(fileURLWithPath: "/tmp/a"),
            destination: .default,
            existingMetadata: Fixtures.Metadata.fileTwo,
            currentMetadata: nil
        )
        let fileThree = try TargetEntity(
            path: URL(fileURLWithPath: "/tmp/a"),
            destination: .default,
            existingMetadata: Fixtures.Metadata.fileThree,
            currentMetadata: nil
        )
        #expect(try compression.decoderFor(entity: fileOne).name == "none")
        #expect(try compression.decoderFor(entity: fileTwo).name == "gzip")
        #expect(try compression.decoderFor(entity: fileThree).name == "deflate")
    }

    @Test("fails to provide decoders for directories")
    func decoderForRejectsDirectory() throws {
        let compression = try Compressions.create(defaultCompression: "deflate", disabledExtensions: "a,b,c")
        let directory = try TargetEntity(
            path: URL(fileURLWithPath: "/tmp/a"),
            destination: .default,
            existingMetadata: Fixtures.Metadata.directoryOne,
            currentMetadata: nil
        )
        #expect(throws: CompressionError.self) {
            _ = try compression.decoderFor(entity: directory)
        }
    }
}
