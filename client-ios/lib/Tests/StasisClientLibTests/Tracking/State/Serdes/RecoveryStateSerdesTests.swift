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

    private static let predefinedOperation = UUID(uuidString: "79879d7a-4113-4b2a-9301-62a5c81343b3")!
    private static let predefinedStateSerialized =
        "CpADCiQ3OTg3OWQ3YS00MTEzLTRiMmEtOTMwMS02MmE1YzgxMzQ" +
        "zYjMS5wIIwN/3n/YtEtMCCg0vdG1wL2ZpbGUvb25lEu8BCg0vdG" +
        "1wL2ZpbGUvb25lEt0BCg0vdG1wL2ZpbGUvb25lEgIKABpjCmEKD" +
        "S90bXAvZmlsZS9vbmUQATCArpmkDzoEcm9vdEIEcm9vdEoJcnd4" +
        "cnd4cnd4UgEBWigKDy90bXAvZmlsZS9vbmVfMBIVCLiFjYW4/b7" +
        "PMhDK96n/wLee7rEBYgRub25lImMKYQoNL3RtcC9maWxlL29uZR" +
        "ABMICumaQPOgRyb290QgRyb290Sglyd3hyd3hyd3hSAQFaKAoPL" +
        "3RtcC9maWxlL29uZV8wEhUIuIWNhbj9vs8yEMr3qf/At57usQFi" +
        "BG5vbmUaFQoNL3RtcC9maWxlL3R3bxIECAMQASIVCg0vdG1wL2Z" +
        "pbGUvb25lEgQIARABKg0vdG1wL2ZpbGUvb25lMhMKDi90bXAvZm" +
        "lsZS9mb3VyEgF4GgF5IKjn95/2LQ=="

    private var predefinedState: [OperationId: RecoveryState] {
        let fileOne = Fixtures.Metadata.fileOne
        let fileTwo = Fixtures.Metadata.fileTwo
        let fileThree = Fixtures.Metadata.fileThree
        let fileOnePath = URL(fileURLWithPath: fileOne.path)
        let fileTwoPath = URL(fileURLWithPath: fileTwo.path)
        let fileThreePath = URL(fileURLWithPath: fileThree.path)
        let fileOneRef = EntityRef.filesystem(fileOnePath)
        let fileTwoRef = EntityRef.filesystem(fileTwoPath)
        let fileThreeRef = EntityRef.filesystem(fileThreePath)
        return [
            Self.predefinedOperation: RecoveryState(
                operation: Self.predefinedOperation,
                started: Date(timeIntervalSince1970: 1_577_930_584),
                entities: RecoveryState.Entities(
                    examined: [fileOneRef],
                    collected: [
                        fileOneRef: try! TargetEntity(
                            ref: fileOneRef,
                            destination: .default,
                            existingMetadata: fileOne,
                            currentMetadata: fileOne
                        )
                    ],
                    pending: [
                        fileTwoRef: RecoveryState.PendingTargetEntity(expectedParts: 3, processedParts: 1)
                    ],
                    processed: [
                        fileOneRef: RecoveryState.ProcessedTargetEntity(expectedParts: 1, processedParts: 1)
                    ],
                    metadataApplied: [fileOneRef],
                    failed: [fileThreeRef: "x"]
                ),
                failures: ["y"],
                completed: Date(timeIntervalSince1970: 1_577_930_585)
            )
        ]
    }

    @Test("round-trips a map of recovery states through the wire format")
    func roundTripsThroughWireFormat() throws {
        let bytes = try serdes.serialize(state)
        let restored = try serdes.deserialize(bytes)
        #expect(restored == state)
    }

    @Test("serialize recovery state to protobuf (predefined)")
    func serializePredefined() throws {
        let bytes = try serdes.serialize(predefinedState)
        #expect(bytes.base64EncodedString() == Self.predefinedStateSerialized)
    }

    @Test("deserialize recovery state from protobuf (predefined)")
    func deserializePredefined() throws {
        let bytes = Data(base64Encoded: Self.predefinedStateSerialized) ?? Data()
        let restored = try serdes.deserialize(bytes)
        #expect(restored == predefinedState)
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
