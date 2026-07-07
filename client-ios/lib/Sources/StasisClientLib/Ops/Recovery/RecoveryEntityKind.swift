import Foundation

public protocol RecoveryEntityKind: Sendable {
    func collector(
        targetMetadata: DatasetMetadata,
        keep: @escaping @Sendable (String, FilesystemMetadata.EntityState) -> Bool,
        destination: TargetEntity.Destination,
        providers: RecoveryProviders
    ) -> any RecoveryCollector
}

public protocol RecoveryFilesystemKind: RecoveryEntityKind {
    func prepare(entity: TargetEntity, ref: URL, providers: RecoveryProviders) throws

    func write(
        entity: TargetEntity,
        ref: URL,
        content: AsyncThrowingStream<Data, Error>,
        providers: RecoveryProviders
    ) async throws

    func applyMetadata(entity: TargetEntity, ref: URL, providers: RecoveryProviders) async throws
}

public protocol RecoveryLibraryKind: RecoveryEntityKind {
    var scheme: String { get }

    func prepare(entity: TargetEntity, scheme: String, path: String, providers: RecoveryProviders) throws

    func write(
        entity: TargetEntity,
        scheme: String,
        path: String,
        content: AsyncThrowingStream<Data, Error>,
        providers: RecoveryProviders
    ) async throws

    func applyMetadata(entity: TargetEntity, scheme: String, path: String, providers: RecoveryProviders) async throws
}

public enum RecoveryEntityKinds {
    public static let filesystem: any RecoveryFilesystemKind = Filesystem()

    public static func prepare(kinds: [any RecoveryEntityKind], entity: TargetEntity, providers: RecoveryProviders) throws {
        switch entity.destinationRef {
        case .filesystem(let url):
            try kinds.compactMap { $0 as? any RecoveryFilesystemKind }
                .first?
                .prepare(entity: entity, ref: url, providers: providers)
        case .library(let scheme, let path):
            try kinds.compactMap { $0 as? any RecoveryLibraryKind }
                .first { $0.scheme == scheme }?
                .prepare(entity: entity, scheme: scheme, path: path, providers: providers)
        }
    }

    public static func write(
        kinds: [any RecoveryEntityKind],
        entity: TargetEntity,
        content: AsyncThrowingStream<Data, Error>,
        providers: RecoveryProviders
    ) async throws {
        switch entity.destinationRef {
        case .filesystem(let url):
            guard let kind = kinds.compactMap({ $0 as? any RecoveryFilesystemKind }).first else {
                throw InvalidArgumentError("No filesystem recovery kind was registered")
            }
            try await kind.write(entity: entity, ref: url, content: content, providers: providers)
        case .library(let scheme, let path):
            guard let kind = kinds.compactMap({ $0 as? any RecoveryLibraryKind }).first(where: { $0.scheme == scheme }) else {
                throw InvalidArgumentError("No recovery kind was registered for scheme [\(scheme)]")
            }
            try await kind.write(entity: entity, scheme: scheme, path: path, content: content, providers: providers)
        }
    }

    public static func applyMetadata(
        kinds: [any RecoveryEntityKind],
        entity: TargetEntity,
        providers: RecoveryProviders
    ) async throws {
        switch entity.destinationRef {
        case .filesystem(let url):
            guard let kind = kinds.compactMap({ $0 as? any RecoveryFilesystemKind }).first else {
                throw InvalidArgumentError("No filesystem recovery kind was registered")
            }
            try await kind.applyMetadata(entity: entity, ref: url, providers: providers)
        case .library(let scheme, let path):
            guard let kind = kinds.compactMap({ $0 as? any RecoveryLibraryKind }).first(where: { $0.scheme == scheme }) else {
                throw InvalidArgumentError("No recovery kind was registered for scheme [\(scheme)]")
            }
            try await kind.applyMetadata(entity: entity, scheme: scheme, path: path, providers: providers)
        }
    }

    private struct Filesystem: RecoveryFilesystemKind {
        func collector(
            targetMetadata: DatasetMetadata,
            keep: @escaping @Sendable (String, FilesystemMetadata.EntityState) -> Bool,
            destination: TargetEntity.Destination,
            providers: RecoveryProviders
        ) -> any RecoveryCollector {
            FilesystemRecoveryCollector(
                targetMetadata: targetMetadata,
                keep: { entity, state in keep(entity, state) && SourceUri.scheme(entity) == nil },
                destination: destination,
                metadataCollector: FilesystemRecoveryMetadataCollector(checksum: providers.checksum),
                clients: providers.clients
            )
        }

        func prepare(entity: TargetEntity, ref: URL, providers: RecoveryProviders) throws {
            let directory: URL = switch entity.existingMetadata {
            case .file, .library: ref.deletingLastPathComponent()
            case .directory: ref
            }
            try FileManager.default.createDirectory(
                at: directory,
                withIntermediateDirectories: true,
                attributes: [.posixPermissions: NSNumber(value: 0o700)]
            )
        }

        func write(
            entity: TargetEntity,
            ref: URL,
            content: AsyncThrowingStream<Data, Error>,
            providers: RecoveryProviders
        ) async throws {
            try await DestagedByteStringSource.destage(content, to: ref, providers: providers)
        }

        func applyMetadata(entity: TargetEntity, ref: URL, providers: RecoveryProviders) async throws {
            try await Metadata.applyEntityMetadataTo(metadata: entity.existingMetadata, entity: ref)
        }
    }
}
