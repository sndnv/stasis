import Foundation

public enum EntityMetadata: Sendable, Equatable, Hashable {
    case file(File)
    case directory(Directory)
    case library(Library)

    public var path: String {
        switch self {
        case .file(let file): file.path
        case .directory(let directory): directory.path
        case .library(let library): library.path
        }
    }

    public var created: Date {
        switch self {
        case .file(let file): file.created
        case .directory(let directory): directory.created
        case .library(let library): library.created
        }
    }

    public var updated: Date {
        switch self {
        case .file(let file): file.updated
        case .directory(let directory): directory.updated
        case .library(let library): library.updated
        }
    }

    public var filesystem: (any EntityFilesystemMetadata)? {
        switch self {
        case .file(let file): file
        case .directory(let directory): directory
        case .library: nil
        }
    }

    public var content: (any EntityContentMetadata)? {
        switch self {
        case .file(let file): file
        case .library(let library): library
        case .directory: nil
        }
    }

    public func asFilesystem() throws -> any EntityFilesystemMetadata {
        guard let filesystem else {
            throw InvalidArgumentError("Requested filesystem metadata but library metadata for [\(path)] found")
        }
        return filesystem
    }

    public func withCrates(_ crates: [String: UUID]) -> EntityMetadata {
        switch self {
        case .file(let file): .file(file.withCrates(crates))
        case .library(let library): .library(library.withCrates(crates))
        case .directory: self
        }
    }

    public func withCompression(_ compression: String) -> EntityMetadata {
        switch self {
        case .file(let file): .file(file.withCompression(compression))
        case .library(let library): .library(library.withCompression(compression))
        case .directory: self
        }
    }

    public func sameKind(as other: EntityMetadata) -> Bool {
        switch (self, other) {
        case (.file, .file), (.directory, .directory), (.library, .library): true
        default: false
        }
    }

    public func hasChanged(comparedTo other: EntityMetadata) -> Bool {
        if let thisContent = content, other.content != nil {
            return self != other.withCompression(thisContent.compression)
        }
        return self != other
    }

    public struct File: Sendable, Equatable, Hashable, EntityFilesystemMetadata, EntityContentMetadata {
        public let path: String
        public let link: String?
        public let isHidden: Bool
        public let created: Date
        public let updated: Date
        public let owner: String
        public let group: String
        public let permissions: String
        public let size: Int64
        public let checksum: Data
        public let crates: [String: UUID]
        public let compression: String

        public init(
            path: String,
            link: String?,
            isHidden: Bool,
            created: Date,
            updated: Date,
            owner: String,
            group: String,
            permissions: String,
            size: Int64,
            checksum: Data,
            crates: [String: UUID],
            compression: String
        ) {
            self.path = path
            self.link = link
            self.isHidden = isHidden
            self.created = created
            self.updated = updated
            self.owner = owner
            self.group = group
            self.permissions = permissions
            self.size = size
            self.checksum = checksum
            self.crates = crates
            self.compression = compression
        }

        public func withCrates(_ crates: [String: UUID]) -> File {
            File(
                path: path,
                link: link,
                isHidden: isHidden,
                created: created,
                updated: updated,
                owner: owner,
                group: group,
                permissions: permissions,
                size: size,
                checksum: checksum,
                crates: crates,
                compression: compression
            )
        }

        public func withCompression(_ compression: String) -> File {
            File(
                path: path,
                link: link,
                isHidden: isHidden,
                created: created,
                updated: updated,
                owner: owner,
                group: group,
                permissions: permissions,
                size: size,
                checksum: checksum,
                crates: crates,
                compression: compression
            )
        }
    }

    public struct Directory: Sendable, Equatable, Hashable, EntityFilesystemMetadata {
        public let path: String
        public let link: String?
        public let isHidden: Bool
        public let created: Date
        public let updated: Date
        public let owner: String
        public let group: String
        public let permissions: String

        public init(
            path: String,
            link: String?,
            isHidden: Bool,
            created: Date,
            updated: Date,
            owner: String,
            group: String,
            permissions: String
        ) {
            self.path = path
            self.link = link
            self.isHidden = isHidden
            self.created = created
            self.updated = updated
            self.owner = owner
            self.group = group
            self.permissions = permissions
        }
    }

    public struct Library: Sendable, Equatable, Hashable, EntityContentMetadata {
        public let path: String
        public let created: Date
        public let updated: Date
        public let size: Int64
        public let checksum: Data
        public let crates: [String: UUID]
        public let compression: String
        public let attributes: Data

        public init(
            path: String,
            created: Date,
            updated: Date,
            size: Int64,
            checksum: Data,
            crates: [String: UUID],
            compression: String,
            attributes: Data
        ) {
            self.path = path
            self.created = created
            self.updated = updated
            self.size = size
            self.checksum = checksum
            self.crates = crates
            self.compression = compression
            self.attributes = attributes
        }

        public func withCrates(_ crates: [String: UUID]) -> Library {
            Library(
                path: path,
                created: created,
                updated: updated,
                size: size,
                checksum: checksum,
                crates: crates,
                compression: compression,
                attributes: attributes
            )
        }

        public func withCompression(_ compression: String) -> Library {
            Library(
                path: path,
                created: created,
                updated: updated,
                size: size,
                checksum: checksum,
                crates: crates,
                compression: compression,
                attributes: attributes
            )
        }
    }
}
