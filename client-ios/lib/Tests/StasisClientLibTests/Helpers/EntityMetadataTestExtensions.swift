import Foundation
@testable import StasisClientLib

extension EntityMetadata {
    func withFileFlags(isHidden: Bool? = nil, checksum: Data? = nil) -> EntityMetadata {
        guard case .file(let file) = self else { return self }
        return .file(EntityMetadata.File(
            path: file.path,
            link: file.link,
            isHidden: isHidden ?? file.isHidden,
            created: file.created,
            updated: file.updated,
            owner: file.owner,
            group: file.group,
            permissions: file.permissions,
            size: file.size,
            checksum: checksum ?? file.checksum,
            crates: file.crates,
            compression: file.compression
        ))
    }

    func with(path: String) -> EntityMetadata {
        switch self {
        case .file(let file):
            .file(EntityMetadata.File(
                path: path,
                link: file.link,
                isHidden: file.isHidden,
                created: file.created,
                updated: file.updated,
                owner: file.owner,
                group: file.group,
                permissions: file.permissions,
                size: file.size,
                checksum: file.checksum,
                crates: file.crates,
                compression: file.compression
            ))
        case .directory(let directory):
            .directory(EntityMetadata.Directory(
                path: path,
                link: directory.link,
                isHidden: directory.isHidden,
                created: directory.created,
                updated: directory.updated,
                owner: directory.owner,
                group: directory.group,
                permissions: directory.permissions
            ))
        case .library(let library):
            .library(EntityMetadata.Library(
                path: path,
                created: library.created,
                updated: library.updated,
                size: library.size,
                checksum: library.checksum,
                crates: library.crates,
                compression: library.compression,
                attributes: library.attributes
            ))
        }
    }
}
