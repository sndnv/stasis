import Foundation
@testable import StasisClientLib
import StasisSharedProto
import Testing

@Suite("EntityMetadata proto bridging")
struct EntityMetadataProtoTests {
    @Test("serializes to protobuf data")
    func serializesToProto() {
        #expect(Fixtures.Metadata.fileOne.proto == Fixtures.Proto.Metadata.fileOneMetadataProto)
        #expect(Fixtures.Metadata.directoryOne.proto == Fixtures.Proto.Metadata.directoryOneMetadataProto)
        #expect(Fixtures.Metadata.fileTwo.proto == Fixtures.Proto.Metadata.fileTwoMetadataProto)
        #expect(Fixtures.Metadata.directoryTwo.proto == Fixtures.Proto.Metadata.directoryTwoMetadataProto)
    }

    @Test("deserializes from valid protobuf data")
    func deserializesFromValidProto() throws {
        let fileOne = try EntityMetadata(proto: Fixtures.Proto.Metadata.fileOneMetadataProto)
        let fileTwo = try EntityMetadata(proto: Fixtures.Proto.Metadata.fileTwoMetadataProto)
        let directoryOne = try EntityMetadata(proto: Fixtures.Proto.Metadata.directoryOneMetadataProto)
        let directoryTwo = try EntityMetadata(proto: Fixtures.Proto.Metadata.directoryTwoMetadataProto)
        #expect(fileOne == Fixtures.Metadata.fileOne)
        #expect(fileTwo == Fixtures.Metadata.fileTwo)
        #expect(directoryOne == Fixtures.Metadata.directoryOne)
        #expect(directoryTwo == Fixtures.Metadata.directoryTwo)
    }

    @Test("fails to deserialize when empty entity is provided")
    func failsOnEmptyEntity() {
        #expect(throws: EntityMetadataError.missingEntity) {
            try EntityMetadata(proto: Fixtures.Proto.Metadata.emptyMetadataProto)
        }
    }
}
