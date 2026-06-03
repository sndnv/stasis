import Foundation

public protocol RecoveryMetadataCollector: Sendable {
    func collect(
        entity: URL,
        destination: TargetEntity.Destination,
        existingMetadata: EntityMetadata
    ) async throws -> TargetEntity
}

public struct DefaultRecoveryMetadataCollector: RecoveryMetadataCollector {
    private let checksum: any Checksum

    public init(checksum: any Checksum) {
        self.checksum = checksum
    }

    public func collect(
        entity: URL,
        destination: TargetEntity.Destination,
        existingMetadata: EntityMetadata
    ) async throws -> TargetEntity {
        try await Metadata.collectTarget(
            checksum: checksum,
            entity: entity,
            destination: destination,
            existingMetadata: existingMetadata
        )
    }
}
