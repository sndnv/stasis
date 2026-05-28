import Foundation
import StasisSharedProto

public enum EntityMetadataError: Error, Equatable {
    case missingEntity
}

extension EntityMetadata {
    public var proto: Stasis_ClientIos_Lib_Model_Proto_EntityMetadata {
        var wrapper = Stasis_ClientIos_Lib_Model_Proto_EntityMetadata()
        switch self {
        case .file(let file):
            var fileProto = Stasis_ClientIos_Lib_Model_Proto_FileMetadata()
            fileProto.path = file.path
            fileProto.size = file.size
            fileProto.link = file.link ?? ""
            fileProto.isHidden = file.isHidden
            fileProto.created = Int64(file.created.timeIntervalSince1970)
            fileProto.updated = Int64(file.updated.timeIntervalSince1970)
            fileProto.owner = file.owner
            fileProto.group = file.group
            fileProto.permissions = file.permissions
            fileProto.checksum = file.checksum
            fileProto.crates = file.crates.mapValues { $0.proto }
            fileProto.compression = file.compression
            wrapper.entity = .file(fileProto)
        case .directory(let directory):
            var directoryProto = Stasis_ClientIos_Lib_Model_Proto_DirectoryMetadata()
            directoryProto.path = directory.path
            directoryProto.link = directory.link ?? ""
            directoryProto.isHidden = directory.isHidden
            directoryProto.created = Int64(directory.created.timeIntervalSince1970)
            directoryProto.updated = Int64(directory.updated.timeIntervalSince1970)
            directoryProto.owner = directory.owner
            directoryProto.group = directory.group
            directoryProto.permissions = directory.permissions
            wrapper.entity = .directory(directoryProto)
        }
        return wrapper
    }

    public init(proto: Stasis_ClientIos_Lib_Model_Proto_EntityMetadata) throws {
        switch proto.entity {
        case .file(let fileProto):
            self = .file(.init(
                path: fileProto.path,
                link: fileProto.link.isEmpty ? nil : fileProto.link,
                isHidden: fileProto.isHidden,
                created: Date(timeIntervalSince1970: TimeInterval(fileProto.created)),
                updated: Date(timeIntervalSince1970: TimeInterval(fileProto.updated)),
                owner: fileProto.owner,
                group: fileProto.group,
                permissions: fileProto.permissions,
                size: fileProto.size,
                checksum: fileProto.checksum,
                crates: fileProto.crates.mapValues { $0.uuid },
                compression: fileProto.compression
            ))
        case .directory(let directoryProto):
            self = .directory(.init(
                path: directoryProto.path,
                link: directoryProto.link.isEmpty ? nil : directoryProto.link,
                isHidden: directoryProto.isHidden,
                created: Date(timeIntervalSince1970: TimeInterval(directoryProto.created)),
                updated: Date(timeIntervalSince1970: TimeInterval(directoryProto.updated)),
                owner: directoryProto.owner,
                group: directoryProto.group,
                permissions: directoryProto.permissions
            ))
        case .none:
            throw EntityMetadataError.missingEntity
        }
    }
}
