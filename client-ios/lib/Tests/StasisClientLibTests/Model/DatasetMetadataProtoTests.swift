import Foundation
@testable import StasisClientLib
import Testing

@Suite("DatasetMetadata proto bridging")
struct DatasetMetadataProtoTests {
    private let datasetMetadata = DatasetMetadata(
        contentChanged: [
            Fixtures.Metadata.fileOne.path: Fixtures.Metadata.fileOne
        ],
        metadataChanged: [
            Fixtures.Metadata.fileTwo.path: Fixtures.Metadata.fileTwo,
            Fixtures.Metadata.directoryOne.path: Fixtures.Metadata.directoryOne,
            Fixtures.Metadata.directoryTwo.path: Fixtures.Metadata.directoryTwo
        ],
        filesystem: FilesystemMetadata(entities: [
            Fixtures.Metadata.fileOne.path: .new,
            Fixtures.Metadata.fileTwo.path: .updated,
            Fixtures.Metadata.directoryOne.path: .new,
            Fixtures.Metadata.directoryTwo.path: .new
        ])
    )

    @Test("round-trips via toByteString / init(byteString:)")
    func roundTripsViaByteString() throws {
        let encoded = try datasetMetadata.toByteString()
        let decoded = try DatasetMetadata(byteString: encoded)
        #expect(decoded == datasetMetadata)
    }

    @Test("emits a gzip-framed payload from toByteString")
    func emitsGzipFraming() throws {
        let encoded = try datasetMetadata.toByteString()
        #expect(encoded.count >= 2)
        #expect(encoded[0] == 0x1f && encoded[1] == 0x8b)
    }

    @Test("fails to deserialize when the byte string is not gzip-framed")
    func failsOnInvalidGzipFraming() {
        let garbage = Data("not a gzip stream".utf8)
        #expect(throws: DatasetMetadataError.self) {
            try DatasetMetadata(byteString: garbage)
        }
    }

    @Test("fails to deserialize when the decompressed payload is not a valid proto")
    func failsOnInvalidProto() throws {
        let garbageProtoBytes = Data("not a proto message at all".utf8)
        let gzippedGarbage = try garbageProtoBytes.gzipped()
        #expect(throws: DatasetMetadataError.self) {
            try DatasetMetadata(byteString: gzippedGarbage)
        }
    }
}
