import Foundation
@testable import StasisClientLib
import StasisSharedProto
import Testing

@Suite("RecoveryStateSerdes")
struct RecoveryStateSerdesTests {
    private let serdes = RecoveryStateSerdes()

    private var state: [OperationId: RecoveryState] {
        [
            Fixtures.State.recoveryOneState.operation: Fixtures.State.recoveryOneState,
            Fixtures.State.recoveryTwoState.operation: Fixtures.State.recoveryTwoState
        ]
    }

    @Test("round-trips a map of recovery states through the wire format")
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
        var collection = Stasis_ClientIos_Lib_Model_Proto_RecoveryStateCollection()
        collection.collection = ["not-a-uuid": Fixtures.State.recoveryOneState.proto]
        let bytes: Data = try collection.serializedBytes()
        #expect(throws: StateSerdesError.invalidOperationId("not-a-uuid")) {
            _ = try serdes.deserialize(bytes)
        }
    }
}
