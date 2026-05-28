import Foundation
@testable import StasisClientLib
import StasisSharedProto
import Testing

@Suite("FilesystemMetadata proto bridging")
struct FilesystemMetadataProtoTests {
    private let entry: DatasetEntryId = UUID(uuidString: "7c98df29-a544-41e5-95ac-463987894fac")!

    @Test("FilesystemMetadata serializes to protobuf data")
    func filesystemMetadataSerializes() {
        #expect(createFilesystemMetadata().proto == createFilesystemMetadataProto())
    }

    @Test("FilesystemMetadata deserializes from valid protobuf data")
    func filesystemMetadataDeserializes() throws {
        let parsed = try FilesystemMetadata(proto: createFilesystemMetadataProto())
        #expect(parsed == createFilesystemMetadata())
    }

    @Test("EntityState serializes to protobuf data")
    func entityStateSerializes() {
        #expect(FilesystemMetadata.EntityState.new.proto == protoEntityStateNew())
        #expect(FilesystemMetadata.EntityState.existing(entry: entry).proto == protoEntityStateExisting(entry: entry))
        #expect(FilesystemMetadata.EntityState.updated.proto == protoEntityStateUpdated())
    }

    @Test("EntityState deserializes from valid protobuf data")
    func entityStateDeserializes() throws {
        let parsedNew = try FilesystemMetadata.EntityState(proto: protoEntityStateNew())
        let parsedExisting = try FilesystemMetadata.EntityState(proto: protoEntityStateExisting(entry: entry))
        let parsedUpdated = try FilesystemMetadata.EntityState(proto: protoEntityStateUpdated())

        #expect(parsedNew == .new)
        #expect(parsedExisting == .existing(entry: entry))
        #expect(parsedUpdated == .updated)
    }

    @Test("EntityState fails when no entry is provided for an existing file state")
    func entityStateFailsOnMissingEntry() {
        #expect(throws: FilesystemMetadataError.missingEntry) {
            try FilesystemMetadata.EntityState(proto: protoEntityStateExisting(entry: nil))
        }
    }

    @Test("EntityState fails when an empty file state is provided")
    func entityStateFailsOnEmptyState() {
        #expect(throws: FilesystemMetadataError.emptyState) {
            try FilesystemMetadata.EntityState(proto: Stasis_ClientIos_Lib_Model_Proto_EntityState())
        }
    }

    // MARK: - helpers

    private func createFilesystemMetadata() -> FilesystemMetadata {
        FilesystemMetadata(entities: [
            "/tmp/file/one": .new,
            "/tmp/file/two": .updated,
            "/tmp/file/four": .existing(entry: entry)
        ])
    }

    private func createFilesystemMetadataProto() -> Stasis_ClientIos_Lib_Model_Proto_FilesystemMetadata {
        var proto = Stasis_ClientIos_Lib_Model_Proto_FilesystemMetadata()
        proto.entities = [
            "/tmp/file/one": protoEntityStateNew(),
            "/tmp/file/two": protoEntityStateUpdated(),
            "/tmp/file/four": protoEntityStateExisting(entry: entry)
        ]
        return proto
    }

    private func protoEntityStateNew() -> Stasis_ClientIos_Lib_Model_Proto_EntityState {
        var proto = Stasis_ClientIos_Lib_Model_Proto_EntityState()
        proto.state = .presentNew(Stasis_ClientIos_Lib_Model_Proto_EntityState.PresentNew())
        return proto
    }

    private func protoEntityStateExisting(
        entry: DatasetEntryId?
    ) -> Stasis_ClientIos_Lib_Model_Proto_EntityState {
        var present = Stasis_ClientIos_Lib_Model_Proto_EntityState.PresentExisting()
        if let entry { present.entry = entry.proto }
        var proto = Stasis_ClientIos_Lib_Model_Proto_EntityState()
        proto.state = .presentExisting(present)
        return proto
    }

    private func protoEntityStateUpdated() -> Stasis_ClientIos_Lib_Model_Proto_EntityState {
        var proto = Stasis_ClientIos_Lib_Model_Proto_EntityState()
        proto.state = .presentUpdated(Stasis_ClientIos_Lib_Model_Proto_EntityState.PresentUpdated())
        return proto
    }
}
