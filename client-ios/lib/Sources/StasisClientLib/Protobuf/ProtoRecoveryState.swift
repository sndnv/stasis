import Foundation
import StasisSharedProto

public enum RecoveryStateError: Error, Equatable {
    case missingEntities
    case missingExistingTargetMetadata
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
        proto.examined = examined.map(\.path)
        proto.collected = collected.mapValues { $0.proto }.keyedByPath()
        proto.pending = pending.mapValues { $0.proto }.keyedByPath()
        proto.processed = processed.mapValues { $0.proto }.keyedByPath()
        proto.metadataApplied = metadataApplied.map(\.path)
        proto.failed = failed.keyedByPath()
        return proto
    }

    public init(proto: Stasis_ClientIos_Lib_Model_Proto_RecoveryEntities) throws {
        self.init(
            examined: Set(proto.examined.map { URL(fileURLWithPath: $0) }),
            collected: try proto.collected.mapValues { try TargetEntity(proto: $0) }.keyedByFileURL(),
            pending: proto.pending.mapValues { RecoveryState.PendingTargetEntity(proto: $0) }.keyedByFileURL(),
            processed: proto.processed.mapValues { RecoveryState.ProcessedTargetEntity(proto: $0) }.keyedByFileURL(),
            metadataApplied: Set(proto.metadataApplied.map { URL(fileURLWithPath: $0) }),
            failed: proto.failed.keyedByFileURL()
        )
    }
}

extension TargetEntity {
    public var proto: Stasis_ClientIos_Lib_Model_Proto_TargetEntity {
        var proto = Stasis_ClientIos_Lib_Model_Proto_TargetEntity()
        proto.path = path.path
        proto.destination = destination.proto
        proto.existingMetadata = existingMetadata.proto
        if let currentMetadata { proto.currentMetadata = currentMetadata.proto }
        return proto
    }

    public init(proto: Stasis_ClientIos_Lib_Model_Proto_TargetEntity) throws {
        guard proto.hasExistingMetadata else { throw RecoveryStateError.missingExistingTargetMetadata }
        try self.init(
            path: URL(fileURLWithPath: proto.path),
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
