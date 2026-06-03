import Foundation
import StasisSharedProto

public enum BackupStateError: Error, Equatable {
    case missingEntities
    case invalidDefinition(String)
    case missingCurrentSourceMetadata
    case missingProcessedSourceEntityMetadata
}

extension BackupState {
    public var proto: Stasis_ClientIos_Lib_Model_Proto_BackupState {
        var proto = Stasis_ClientIos_Lib_Model_Proto_BackupState()
        proto.definition = definition.uuidString.lowercased()
        proto.started = started.epochMillis
        proto.entities = entities.proto
        if let metadataCollected { proto.metadataCollected = metadataCollected.epochMillis }
        if let metadataPushed { proto.metadataPushed = metadataPushed.epochMillis }
        proto.failures = failures
        if let completed { proto.completed = completed.epochMillis }
        return proto
    }

    public static func from(
        operation: OperationId,
        proto: Stasis_ClientIos_Lib_Model_Proto_BackupState
    ) throws -> BackupState {
        guard proto.hasEntities else { throw BackupStateError.missingEntities }
        guard let definition = UUID(uuidString: proto.definition) else {
            throw BackupStateError.invalidDefinition(proto.definition)
        }
        return BackupState(
            operation: operation,
            definition: definition,
            started: Date(epochMillis: proto.started),
            entities: try BackupState.Entities(proto: proto.entities),
            metadataCollected: proto.hasMetadataCollected ? Date(epochMillis: proto.metadataCollected) : nil,
            metadataPushed: proto.hasMetadataPushed ? Date(epochMillis: proto.metadataPushed) : nil,
            failures: proto.failures,
            completed: proto.hasCompleted ? Date(epochMillis: proto.completed) : nil
        )
    }
}

extension BackupState.Entities {
    public var proto: Stasis_ClientIos_Lib_Model_Proto_BackupEntities {
        var proto = Stasis_ClientIos_Lib_Model_Proto_BackupEntities()
        proto.discovered = discovered.map(\.path)
        proto.unmatched = unmatched
        proto.examined = examined.map(\.path)
        proto.skipped = skipped.map(\.path)
        proto.collected = collected.mapValues { $0.proto }.keyedByPath()
        proto.pending = pending.mapValues { $0.proto }.keyedByPath()
        proto.processed = processed.mapValues { $0.proto }.keyedByPath()
        proto.failed = failed.keyedByPath()
        return proto
    }

    public init(proto: Stasis_ClientIos_Lib_Model_Proto_BackupEntities) throws {
        self.init(
            discovered: Set(proto.discovered.map { URL(fileURLWithPath: $0) }),
            unmatched: proto.unmatched,
            examined: Set(proto.examined.map { URL(fileURLWithPath: $0) }),
            skipped: Set(proto.skipped.map { URL(fileURLWithPath: $0) }),
            collected: try proto.collected.mapValues { try SourceEntity(proto: $0) }.keyedByFileURL(),
            pending: proto.pending.mapValues { BackupState.PendingSourceEntity(proto: $0) }.keyedByFileURL(),
            processed: try proto.processed
                .mapValues { try BackupState.ProcessedSourceEntity(proto: $0) }
                .keyedByFileURL(),
            failed: proto.failed.keyedByFileURL()
        )
    }
}

extension SourceEntity {
    public var proto: Stasis_ClientIos_Lib_Model_Proto_SourceEntity {
        var proto = Stasis_ClientIos_Lib_Model_Proto_SourceEntity()
        proto.path = path.path
        if let existingMetadata { proto.existingMetadata = existingMetadata.proto }
        proto.currentMetadata = currentMetadata.proto
        return proto
    }

    public init(proto: Stasis_ClientIos_Lib_Model_Proto_SourceEntity) throws {
        guard proto.hasCurrentMetadata else { throw BackupStateError.missingCurrentSourceMetadata }
        try self.init(
            path: URL(fileURLWithPath: proto.path),
            existingMetadata: proto.hasExistingMetadata ? try EntityMetadata(proto: proto.existingMetadata) : nil,
            currentMetadata: try EntityMetadata(proto: proto.currentMetadata)
        )
    }
}

extension BackupState.PendingSourceEntity {
    public var proto: Stasis_ClientIos_Lib_Model_Proto_PendingSourceEntity {
        var proto = Stasis_ClientIos_Lib_Model_Proto_PendingSourceEntity()
        proto.expectedParts = UInt32(expectedParts)
        proto.processedParts = UInt32(processedParts)
        return proto
    }

    public init(proto: Stasis_ClientIos_Lib_Model_Proto_PendingSourceEntity) {
        self.init(expectedParts: Int(proto.expectedParts), processedParts: Int(proto.processedParts))
    }
}

extension BackupState.ProcessedSourceEntity {
    public var proto: Stasis_ClientIos_Lib_Model_Proto_ProcessedSourceEntity {
        var proto = Stasis_ClientIos_Lib_Model_Proto_ProcessedSourceEntity()
        proto.expectedParts = UInt32(expectedParts)
        proto.processedParts = UInt32(processedParts)
        switch metadata {
        case .left(let value): proto.metadata = .left(value.proto)
        case .right(let value): proto.metadata = .right(value.proto)
        }
        return proto
    }

    public init(proto: Stasis_ClientIos_Lib_Model_Proto_ProcessedSourceEntity) throws {
        let metadata: Either<EntityMetadata, EntityMetadata> = switch proto.metadata {
        case .left(let value): .left(try EntityMetadata(proto: value))
        case .right(let value): .right(try EntityMetadata(proto: value))
        case .none: throw BackupStateError.missingProcessedSourceEntityMetadata
        }
        self.init(
            expectedParts: Int(proto.expectedParts),
            processedParts: Int(proto.processedParts),
            metadata: metadata
        )
    }
}
