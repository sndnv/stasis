import Foundation
@testable import StasisClientLib

struct MockRecoveryEntityKind: RecoveryFilesystemKind {
    let recoveryCollector: any RecoveryCollector

    func collector(
        targetMetadata: DatasetMetadata,
        keep: @escaping @Sendable (String, FilesystemMetadata.EntityState) -> Bool,
        destination: TargetEntity.Destination,
        providers: RecoveryProviders
    ) -> any RecoveryCollector {
        recoveryCollector
    }

    func prepare(entity: TargetEntity, ref: URL, providers: RecoveryProviders) throws {
        try RecoveryEntityKinds.filesystem.prepare(entity: entity, ref: ref, providers: providers)
    }

    func write(
        entity: TargetEntity,
        ref: URL,
        content: AsyncThrowingStream<Data, Error>,
        providers: RecoveryProviders
    ) async throws {
        try await RecoveryEntityKinds.filesystem.write(entity: entity, ref: ref, content: content, providers: providers)
    }

    func applyMetadata(entity: TargetEntity, ref: URL, providers: RecoveryProviders) async throws {
        try await RecoveryEntityKinds.filesystem.applyMetadata(entity: entity, ref: ref, providers: providers)
    }
}
