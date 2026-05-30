import Darwin
import Foundation

public enum Metadata {
    public static func collectSource(
        checksum: any Checksum,
        compression: any Compression,
        entity: URL,
        existingMetadata: EntityMetadata?
    ) async throws -> SourceEntity {
        let baseMetadata = try await extractBaseEntityMetadata(entity: entity)

        let entityMetadata = try await collectEntityMetadata(
            currentMetadata: baseMetadata,
            checksum: checksum,
            collectCrates: { currentChecksum in
                try collectCratesForSourceFile(existingMetadata: existingMetadata, currentChecksum: currentChecksum)
            },
            collectCompression: { compression.algorithmFor(entity: entity) }
        )

        return try SourceEntity(
            path: entity,
            existingMetadata: existingMetadata,
            currentMetadata: entityMetadata
        )
    }

    public static func collectTarget(
        checksum: any Checksum,
        entity: URL,
        destination: TargetEntity.Destination,
        existingMetadata: EntityMetadata
    ) async throws -> TargetEntity {
        var targetEntity = try TargetEntity(
            path: entity,
            destination: destination,
            existingMetadata: existingMetadata,
            currentMetadata: nil
        )

        let destinationPath = targetEntity.destinationPath

        guard FileManager.default.fileExists(atPath: destinationPath.path) else {
            return targetEntity
        }

        let baseMetadata = try await extractBaseEntityMetadata(entity: destinationPath)

        let entityMetadata = try await collectEntityMetadata(
            currentMetadata: baseMetadata,
            checksum: checksum,
            collectCrates: { _ in try collectCratesForTargetFile(existingMetadata: existingMetadata) },
            collectCompression: { try collectCompressionForTargetFile(existingMetadata: existingMetadata) }
        )

        targetEntity = try TargetEntity(
            path: targetEntity.path,
            destination: targetEntity.destination,
            existingMetadata: existingMetadata,
            currentMetadata: entityMetadata
        )
        return targetEntity
    }

    public static func collectEntityMetadata(
        currentMetadata: BaseEntityMetadata,
        checksum: any Checksum,
        collectCrates: (Data) throws -> [String: UUID],
        collectCompression: () throws -> String
    ) async throws -> EntityMetadata {
        if currentMetadata.isDirectory {
            return .directory(.init(
                path: currentMetadata.path.path,
                link: currentMetadata.link?.path,
                isHidden: currentMetadata.isHidden,
                created: currentMetadata.created,
                updated: currentMetadata.updated,
                owner: currentMetadata.owner,
                group: currentMetadata.group,
                permissions: currentMetadata.permissions
            ))
        }
        let currentChecksum = try await checksum.calculate(file: currentMetadata.path)
        let crates = try collectCrates(currentChecksum)
        let compression = try collectCompression()
        return .file(.init(
            path: currentMetadata.path.path,
            link: currentMetadata.link?.path,
            isHidden: currentMetadata.isHidden,
            created: currentMetadata.created,
            updated: currentMetadata.updated,
            owner: currentMetadata.owner,
            group: currentMetadata.group,
            permissions: currentMetadata.permissions,
            size: currentMetadata.size,
            checksum: currentChecksum,
            crates: crates,
            compression: compression
        ))
    }

    public static func collectCratesForSourceFile(
        existingMetadata: EntityMetadata?,
        currentChecksum: Data
    ) throws -> [String: UUID] {
        switch existingMetadata {
        case .file(let file):
            return file.checksum == currentChecksum ? file.crates : [:]
        case .directory(let directory):
            throw MetadataError.expectedFileGotDirectory(path: directory.path)
        case .none:
            return [:]
        }
    }

    public static func collectCratesForTargetFile(
        existingMetadata: EntityMetadata
    ) throws -> [String: UUID] {
        switch existingMetadata {
        case .file(let file):
            return file.crates
        case .directory(let directory):
            throw MetadataError.expectedFileGotDirectory(path: directory.path)
        }
    }

    public static func collectCompressionForTargetFile(
        existingMetadata: EntityMetadata
    ) throws -> String {
        switch existingMetadata {
        case .file(let file):
            return file.compression
        case .directory(let directory):
            throw MetadataError.expectedFileGotDirectory(path: directory.path)
        }
    }

    public static func extractBaseEntityMetadata(entity: URL) async throws -> BaseEntityMetadata {
        try await Task.detached(priority: .userInitiated) {
            let path = entity.path
            let attributes = try FileManager.default.attributesOfItem(atPath: path)

            let type = attributes[.type] as? FileAttributeType
            let isDirectory = type == .typeDirectory
            let isSymbolicLink = type == .typeSymbolicLink

            let link: URL?
            if isSymbolicLink {
                let target = try FileManager.default.destinationOfSymbolicLink(atPath: path)
                link = URL(fileURLWithPath: target)
            } else {
                link = nil
            }

            let isHidden = entity.lastPathComponent.hasPrefix(".")
            let created = (attributes[.creationDate] as? Date) ?? Date(timeIntervalSince1970: 0)
            let updated = (attributes[.modificationDate] as? Date) ?? Date(timeIntervalSince1970: 0)
            let owner = (attributes[.ownerAccountName] as? String)
                ?? (attributes[.ownerAccountID] as? NSNumber)?.stringValue
                ?? "0"
            let group = (attributes[.groupOwnerAccountName] as? String)
                ?? (attributes[.groupOwnerAccountID] as? NSNumber)?.stringValue
                ?? "0"
            let mode = mode_t((attributes[.posixPermissions] as? NSNumber)?.uint16Value ?? 0)
            let permissions = formatPosixPermissions(mode: mode)
            let size = (attributes[.size] as? NSNumber)?.int64Value ?? 0

            return BaseEntityMetadata(
                path: entity,
                isDirectory: isDirectory,
                link: link,
                isHidden: isHidden,
                created: created,
                updated: updated,
                owner: owner,
                group: group,
                permissions: permissions,
                size: size
            )
        }.value
    }

    public static func applyEntityMetadataTo(metadata: EntityMetadata, entity: URL) async throws {
        try await Task.detached(priority: .userInitiated) {
            let path = entity.path
            let mode = parsePosixPermissions(metadata.permissions)

            try FileManager.default.setAttributes(
                [
                    .posixPermissions: NSNumber(value: mode),
                    .modificationDate: metadata.updated
                ],
                ofItemAtPath: path
            )

            try? FileManager.default.setAttributes(
                [
                    .ownerAccountName: metadata.owner,
                    .groupOwnerAccountName: metadata.group
                ],
                ofItemAtPath: path
            )
        }.value
    }

    public struct BaseEntityMetadata: Sendable, Equatable {
        public let path: URL
        public let isDirectory: Bool
        public let link: URL?
        public let isHidden: Bool
        public let created: Date
        public let updated: Date
        public let owner: String
        public let group: String
        public let permissions: String
        public let size: Int64
    }
}

public enum MetadataError: Error, Equatable, Sendable {
    case expectedFileGotDirectory(path: String)
}

private func formatPosixPermissions(mode: mode_t) -> String {
    let bits: [(mode_t, Character)] = [
        (S_IRUSR, "r"), (S_IWUSR, "w"), (S_IXUSR, "x"),
        (S_IRGRP, "r"), (S_IWGRP, "w"), (S_IXGRP, "x"),
        (S_IROTH, "r"), (S_IWOTH, "w"), (S_IXOTH, "x")
    ]
    var result = ""
    for (bit, char) in bits {
        result.append(mode & bit != 0 ? char : "-")
    }
    return result
}

private func parsePosixPermissions(_ value: String) -> mode_t {
    let chars = Array(value)
    guard chars.count == 9 else { return 0 }
    let bits: [(Int, mode_t)] = [
        (0, S_IRUSR), (1, S_IWUSR), (2, S_IXUSR),
        (3, S_IRGRP), (4, S_IWGRP), (5, S_IXGRP),
        (6, S_IROTH), (7, S_IWOTH), (8, S_IXOTH)
    ]
    var mode: mode_t = 0
    for (index, bit) in bits where chars[index] != "-" {
        mode |= bit
    }
    return mode
}
