import Foundation
import StasisSharedProto

public enum RecoveryStateError: Error, Equatable, LocalizedError {
    case missingEntities
    case missingExistingTargetMetadata

    public var errorDescription: String? {
        switch self {
        case .missingEntities:
            "Recovery state is missing its entities"
        case .missingExistingTargetMetadata:
            "Recovery state is missing existing target metadata"
        }
    }
}

extension RecoveryState {
    public var proto: Stasis_ClientIos_Lib_Model_Proto_RecoveryState {
        var proto = Stasis_ClientIos_Lib_Model_Proto_RecoveryState()
        proto.started = started.epochMillis
        proto.entities = entities.proto
        proto.failures = failures
        if let completed { proto.completed = completed.epochMillis }
        return proto
    }

    public static func from(
        operation: OperationId,
        proto: Stasis_ClientIos_Lib_Model_Proto_RecoveryState
    ) throws -> RecoveryState {
        guard proto.hasEntities else { throw RecoveryStateError.missingEntities }
        return RecoveryState(
            operation: operation,
            started: Date(epochMillis: proto.started),
            entities: try RecoveryState.Entities(proto: proto.entities),
            failures: proto.failures,
            completed: proto.hasCompleted ? Date(epochMillis: proto.completed) : nil
        )
    }
}

extension RecoveryState.Entities {
    public var proto: Stasis_ClientIos_Lib_Model_Proto_RecoveryEntities {
        var proto = Stasis_ClientIos_Lib_Model_Proto_RecoveryEntities()
        proto.examined = examined.map(\.key)
        proto.collected = collected.mapValues { $0.proto }.keyedByKey()
        proto.pending = pending.mapValues { $0.proto }.keyedByKey()
        proto.processed = processed.mapValues { $0.proto }.keyedByKey()
        proto.metadataApplied = metadataApplied.map(\.key)
        proto.failed = failed.keyedByKey()
        return proto
    }

    public init(proto: Stasis_ClientIos_Lib_Model_Proto_RecoveryEntities) throws {
        self.init(
            examined: Set(proto.examined.map { EntityRef.default(key: $0) }),
            collected: try proto.collected.mapValues { try TargetEntity(proto: $0) }.keyedByRef(),
            pending: proto.pending.mapValues { RecoveryState.PendingTargetEntity(proto: $0) }.keyedByRef(),
            processed: proto.processed.mapValues { RecoveryState.ProcessedTargetEntity(proto: $0) }.keyedByRef(),
            metadataApplied: Set(proto.metadataApplied.map { EntityRef.default(key: $0) }),
            failed: proto.failed.keyedByRef()
        )
    }
}

extension TargetEntity {
    public var proto: Stasis_ClientIos_Lib_Model_Proto_TargetEntity {
        var proto = Stasis_ClientIos_Lib_Model_Proto_TargetEntity()
        proto.ref = ref.key
        proto.destination = destination.proto
        proto.existingMetadata = existingMetadata.proto
        if let currentMetadata { proto.currentMetadata = currentMetadata.proto }
        return proto
    }

    public init(proto: Stasis_ClientIos_Lib_Model_Proto_TargetEntity) throws {
        guard proto.hasExistingMetadata else { throw RecoveryStateError.missingExistingTargetMetadata }
        try self.init(
            ref: EntityRef.default(key: proto.ref),
            destination: TargetEntity.Destination(proto: proto.destination),
            existingMetadata: try EntityMetadata(proto: proto.existingMetadata),
            currentMetadata: proto.hasCurrentMetadata ? try EntityMetadata(proto: proto.currentMetadata) : nil
        )
    }
}

extension TargetEntity.Destination {
    public var proto: Stasis_ClientIos_Lib_Model_Proto_TargetEntityDestination {
        var proto = Stasis_ClientIos_Lib_Model_Proto_TargetEntityDestination()
        switch self {
        case .default:
            proto.sealedValue = .default(.init())
        case .directory(let path, let keepDefaultStructure):
            var directory = Stasis_ClientIos_Lib_Model_Proto_TargetEntityDestinationDirectory()
            directory.path = path.path
            directory.keepDefaultStructure = keepDefaultStructure
            proto.sealedValue = .directory(directory)
        }
        return proto
    }

    public init(proto: Stasis_ClientIos_Lib_Model_Proto_TargetEntityDestination) {
        switch proto.sealedValue {
        case .directory(let directory):
            self = .directory(
                path: URL(fileURLWithPath: directory.path),
                keepDefaultStructure: directory.keepDefaultStructure
            )
        case .default, .none:
            self = .default
        }
    }
}

extension RecoveryState.PendingTargetEntity {
    public var proto: Stasis_ClientIos_Lib_Model_Proto_PendingTargetEntity {
        var proto = Stasis_ClientIos_Lib_Model_Proto_PendingTargetEntity()
        proto.expectedParts = UInt32(expectedParts)
        proto.processedParts = UInt32(processedParts)
        return proto
    }

    public init(proto: Stasis_ClientIos_Lib_Model_Proto_PendingTargetEntity) {
        self.init(expectedParts: Int(proto.expectedParts), processedParts: Int(proto.processedParts))
    }
}

extension RecoveryState.ProcessedTargetEntity {
    public var proto: Stasis_ClientIos_Lib_Model_Proto_ProcessedTargetEntity {
        var proto = Stasis_ClientIos_Lib_Model_Proto_ProcessedTargetEntity()
        proto.expectedParts = UInt32(expectedParts)
        proto.processedParts = UInt32(processedParts)
        return proto
    }

    public init(proto: Stasis_ClientIos_Lib_Model_Proto_ProcessedTargetEntity) {
        self.init(expectedParts: Int(proto.expectedParts), processedParts: Int(proto.processedParts))
    }
}
