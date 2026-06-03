import Foundation
import StasisSharedProto

public struct RecoveryStateSerdes: StateStoreSerdes {
    public init() {}

    public func serialize(_ state: [OperationId: RecoveryState]) throws -> Data {
        var collection = Stasis_ClientIos_Lib_Model_Proto_RecoveryStateCollection()
        collection.collection = state.reduce(into: [:]) { acc, pair in
            acc[pair.key.uuidString.lowercased()] = pair.value.proto
        }
        return try collection.serializedBytes()
    }

    public func deserialize(_ bytes: Data) throws -> [OperationId: RecoveryState] {
        let collection = try Stasis_ClientIos_Lib_Model_Proto_RecoveryStateCollection(serializedBytes: bytes)
        var result: [OperationId: RecoveryState] = [:]
        for (key, value) in collection.collection {
            guard let operation = UUID(uuidString: key) else {
                throw StateSerdesError.invalidOperationId(key)
            }
            result[operation] = try RecoveryState.from(operation: operation, proto: value)
        }
        return result
    }
}
