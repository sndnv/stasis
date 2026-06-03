import Foundation

public protocol BackupMetadataCollector: Sendable {
    func collect(entity: URL, existingMetadata: EntityMetadata?) async throws -> SourceEntity
}

public struct DefaultBackupMetadataCollector: BackupMetadataCollector {
    private let checksum: any Checksum
    private let compression: any Compression

    public init(checksum: any Checksum, compression: any Compression) {
        self.checksum = checksum
        self.compression = compression
    }

    public func collect(entity: URL, existingMetadata: EntityMetadata?) async throws -> SourceEntity {
        try await Metadata.collectSource(
            checksum: checksum,
            compression: compression,
            entity: entity,
            existingMetadata: existingMetadata
        )
    }
}
