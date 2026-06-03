import Foundation
@testable import StasisClientLib
import StasisSharedProto
import Testing

@Suite("BackupStateSerdes")
struct BackupStateSerdesTests {
    private let serdes = BackupStateSerdes()

    private var state: [OperationId: BackupState] {
        [
            Fixtures.State.backupOneState.operation: Fixtures.State.backupOneState,
            Fixtures.State.backupTwoState.operation: Fixtures.State.backupTwoState
        ]
    }

    @Test("round-trips a map of backup states through the wire format")
    func roundTripsThroughWireFormat() throws {
        let bytes = try serdes.serialize(state)
        let restored = try serdes.deserialize(bytes)
        #expect(restored == state)
    }

    @Test("deserialize on empty bytes yields an empty map")
    func deserializeEmptyBytes() throws {
        let restored = try serdes.deserialize(Data())
        #expect(restored.isEmpty)
    }

    @Test("deserialize throws on malformed operation-id keys")
    func deserializeRejectsInvalidKeys() throws {
        var collection = Stasis_ClientIos_Lib_Model_Proto_BackupStateCollection()
        collection.collection = ["not-a-uuid": Fixtures.State.backupTwoState.proto]
        let bytes: Data = try collection.serializedBytes()
        #expect(throws: StateSerdesError.invalidOperationId("not-a-uuid")) {
            _ = try serdes.deserialize(bytes)
        }
    }
}
