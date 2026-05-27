import Foundation

public enum EntityMetadata: Sendable, Equatable, Hashable {
    case file(File)
    case directory(Directory)

    public var path: String {
        switch self {
        case .file(let file): file.path
        case .directory(let directory): directory.path
        }
    }

    public var link: String? {
        switch self {
        case .file(let file): file.link
        case .directory(let directory): directory.link
        }
    }

    public var isHidden: Bool {
        switch self {
        case .file(let file): file.isHidden
        case .directory(let directory): directory.isHidden
        }
    }

    public var created: Date {
        switch self {
        case .file(let file): file.created
        case .directory(let directory): directory.created
        }
    }

    public var updated: Date {
        switch self {
        case .file(let file): file.updated
        case .directory(let directory): directory.updated
        }
    }

    public var owner: String {
        switch self {
        case .file(let file): file.owner
        case .directory(let directory): directory.owner
        }
    }

    public var group: String {
        switch self {
        case .file(let file): file.group
        case .directory(let directory): directory.group
        }
    }

    public var permissions: String {
        switch self {
        case .file(let file): file.permissions
        case .directory(let directory): directory.permissions
        }
    }

    public func sameKind(as other: EntityMetadata) -> Bool {
        switch (self, other) {
        case (.file, .file), (.directory, .directory): true
        default: false
        }
    }

    public func hasChanged(comparedTo other: EntityMetadata) -> Bool {
        if case .file(let lhs) = self, case .file(let rhs) = other {
            return lhs != File(
                path: rhs.path,
                link: rhs.link,
                isHidden: rhs.isHidden,
                created: rhs.created,
                updated: rhs.updated,
                owner: rhs.owner,
                group: rhs.group,
                permissions: rhs.permissions,
                size: rhs.size,
                checksum: rhs.checksum,
                crates: rhs.crates,
                compression: lhs.compression
            )
        }
        return self != other
    }

    public struct File: Sendable, Equatable, Hashable {
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
    }

    public struct Directory: Sendable, Equatable, Hashable {
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
}
