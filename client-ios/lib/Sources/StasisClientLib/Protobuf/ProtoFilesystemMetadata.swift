import Foundation
import fsi
import StasisSharedProto

public enum FilesystemMetadataError: Error, Equatable, LocalizedError {
    case missingEntry
    case emptyState

    public var errorDescription: String? {
        switch self {
        case .missingEntry:
            "Filesystem metadata state is missing its entry reference"
        case .emptyState:
            "Filesystem metadata contains an empty entity state"
        }
    }
}

extension FilesystemMetadata {
    public init(entities: [String: EntityState]) {
        self = .asTrie(underlying: TrieIndex(entities))
    }

    public var proto: Stasis_ClientIos_Lib_Model_Proto_FilesystemMetadata {
        var result: [String: Stasis_ClientIos_Lib_Model_Proto_EntityState] = [:]
        underlying.forEach { path, state in
            result[path] = state.proto
        }
        var proto = Stasis_ClientIos_Lib_Model_Proto_FilesystemMetadata()
        proto.entities = result
        return proto
    }

    public init(proto: Stasis_ClientIos_Lib_Model_Proto_FilesystemMetadata) throws {
        var entities: [String: EntityState] = [:]
        for (path, stateProto) in proto.entities {
            entities[path] = try EntityState(proto: stateProto)
        }
        self.init(entities: entities)
    }
}

extension FilesystemMetadata.EntityState {
    public var proto: Stasis_ClientIos_Lib_Model_Proto_EntityState {
        var proto = Stasis_ClientIos_Lib_Model_Proto_EntityState()
        switch self {
        case .new:
            proto.state = .presentNew(Stasis_ClientIos_Lib_Model_Proto_EntityState.PresentNew())
        case .existing(let entry):
            var present = Stasis_ClientIos_Lib_Model_Proto_EntityState.PresentExisting()
            present.entry = entry.proto
            proto.state = .presentExisting(present)
        case .updated:
            proto.state = .presentUpdated(Stasis_ClientIos_Lib_Model_Proto_EntityState.PresentUpdated())
        }
        return proto
    }

    public init(proto: Stasis_ClientIos_Lib_Model_Proto_EntityState) throws {
        switch proto.state {
        case .presentNew:
            self = .new
        case .presentExisting(let present):
            guard present.hasEntry else {
                throw FilesystemMetadataError.missingEntry
            }
            self = .existing(entry: present.entry.uuid)
        case .presentUpdated:
            self = .updated
        case .none:
            throw FilesystemMetadataError.emptyState
        }
    }
}
