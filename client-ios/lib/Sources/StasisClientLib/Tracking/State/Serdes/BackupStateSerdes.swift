import Foundation
import StasisSharedProto

public struct BackupStateSerdes: StateStoreSerdes {
    public init() {}

    public func serialize(_ state: [OperationId: BackupState]) throws -> Data {
        var collection = Stasis_ClientIos_Lib_Model_Proto_BackupStateCollection()
        collection.collection = state.reduce(into: [:]) { acc, pair in
            acc[pair.key.uuidString.lowercased()] = pair.value.proto
        }
        return try collection.serializedBytes()
    }

    public func deserialize(_ bytes: Data) throws -> [OperationId: BackupState] {
        let collection = try Stasis_ClientIos_Lib_Model_Proto_BackupStateCollection(serializedBytes: bytes)
        var result: [OperationId: BackupState] = [:]
        for (key, value) in collection.collection {
            guard let operation = UUID(uuidString: key) else {
                throw StateSerdesError.invalidOperationId(key)
            }
            result[operation] = try BackupState.from(operation: operation, proto: value)
        }
        return result
    }
}
