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

    private static let predefinedOperation = UUID(uuidString: "1a00ae68-d601-4ade-b849-33c7e50c46cf")!
    private static let predefinedStateSerialized =
        "CrgECiQxYTAwYWU2OC1kNjAxLTRhZGUtYjg0OS0zM2M3ZTUwYzQ" +
        "2Y2YSjwQKJDg4MGExY2NiLWQ5MzItNDgyNi04MGU2LTQ0N2IzM2" +
        "EzMWQwZRD4pZie9i0axwMKDS90bXAvZmlsZS9vbmUSAWEaDS90b" +
        "XAvZmlsZS90d28i6wEKDS90bXAvZmlsZS9vbmUS2QEKDS90bXAv" +
        "ZmlsZS9vbmUSYwphCg0vdG1wL2ZpbGUvb25lEAEwgK6ZpA86BHJ" +
        "vb3RCBHJvb3RKCXJ3eHJ3eHJ3eFIBAVooCg8vdG1wL2ZpbGUvb2" +
        "5lXzASFQi4hY2FuP2+zzIQyvep/8C3nu6xAWIEbm9uZRpjCmEKD" +
        "S90bXAvZmlsZS9vbmUQATCArpmkDzoEcm9vdEIEcm9vdEoJcnd4" +
        "cnd4cnd4UgEBWigKDy90bXAvZmlsZS9vbmVfMBIVCLiFjYW4/b7" +
        "PMhDK96n/wLee7rEBYgRub25lKhUKDS90bXAvZmlsZS90d28SBA" +
        "gBEAIyegoNL3RtcC9maWxlL29uZRJpCAEQARpjCmEKDS90bXAvZ" +
        "mlsZS9vbmUQATCArpmkDzoEcm9vdEIEcm9vdEoJcnd4cnd4cnd4" +
        "UgEBWigKDy90bXAvZmlsZS9vbmVfMBIVCLiFjYW4/b7PMhDK96n" +
        "/wLee7rEBYgRub25lOhMKDi90bXAvZmlsZS9mb3VyEgF4Qg4vdG" +
        "1wL2ZpbGUvZm91ciDgrZie9i0oyLWYnvYtMgF5OLC9mJ72LQ=="

    private var predefinedState: [OperationId: BackupState] {
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
            Self.predefinedOperation: BackupState(
                operation: Self.predefinedOperation,
                definition: UUID(uuidString: "880a1ccb-d932-4826-80e6-447b33a31d0e")!,
                started: Date(timeIntervalSince1970: 1_577_926_923),
                entities: BackupState.Entities(
                    discovered: [fileOneRef],
                    unmatched: ["a"],
                    examined: [fileTwoRef],
                    skipped: [fileThreeRef],
                    collected: [
                        fileOneRef: try! SourceEntity(
                            ref: fileOneRef,
                            existingMetadata: fileOne,
                            currentMetadata: fileOne
                        )
                    ],
                    pending: [
                        fileTwoRef: BackupState.PendingSourceEntity(expectedParts: 1, processedParts: 2)
                    ],
                    processed: [
                        fileOneRef: BackupState.ProcessedSourceEntity(
                            expectedParts: 1, processedParts: 1, metadata: .left(fileOne)
                        )
                    ],
                    failed: [fileThreeRef: "x"]
                ),
                metadataCollected: Date(timeIntervalSince1970: 1_577_926_924),
                metadataPushed: Date(timeIntervalSince1970: 1_577_926_925),
                failures: ["y"],
                completed: Date(timeIntervalSince1970: 1_577_926_926)
            )
        ]
    }

    @Test("round-trips a map of backup states through the wire format")
    func roundTripsThroughWireFormat() throws {
        let bytes = try serdes.serialize(state)
        let restored = try serdes.deserialize(bytes)
        #expect(restored == state)
    }

    @Test("serialize backup state to protobuf (predefined)")
    func serializePredefined() throws {
        let bytes = try serdes.serialize(predefinedState)
        #expect(bytes.base64EncodedString() == Self.predefinedStateSerialized)
    }

    @Test("deserialize backup state from protobuf (predefined)")
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
        var collection = Stasis_ClientIos_Lib_Model_Proto_BackupStateCollection()
        collection.collection = ["not-a-uuid": Fixtures.State.backupTwoState.proto]
        let bytes: Data = try collection.serializedBytes()
        #expect(throws: StateSerdesError.invalidOperationId("not-a-uuid")) {
            _ = try serdes.deserialize(bytes)
        }
    }
}
