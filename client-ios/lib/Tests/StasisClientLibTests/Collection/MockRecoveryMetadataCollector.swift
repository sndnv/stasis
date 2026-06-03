import Foundation
@testable import StasisClientLib

struct MockRecoveryMetadataCollector: RecoveryMetadataCollector {
    let metadata: [URL: EntityMetadata]

    func collect(
        entity: URL,
        destination: TargetEntity.Destination,
        existingMetadata: EntityMetadata
    ) async throws -> TargetEntity {
        let current = metadata[entity]
        return try TargetEntity(
            path: entity,
            destination: destination,
            existingMetadata: existingMetadata,
            currentMetadata: current
        )
    }
}
