import Foundation
import StasisClientLib
import Synchronization

final class PhotosEntityKind: BackupLibraryKind, RecoveryLibraryKind, Sendable {
    let scheme = PhotoAlbumSelector.scheme

    private let library: any PhotoLibrary
    private let pending = Mutex<[String: URL]>([:])

    init(library: any PhotoLibrary) {
        self.library = library
    }

    func collector(
        operation: OperationId,
        collector: Backup.EntityDiscovery.Collector,
        latestMetadata: DatasetMetadata?,
        providers: BackupProviders
    ) async throws -> any BackupCollector {
        guard library.hasReadAccess() else {
            await providers.track.failureEncountered(
                operation: operation,
                failure: PhotoLibraryPermissionMissing(scheme: scheme)
            )
            return EmptyBackupCollector()
        }
        return Collector(
            kind: self,
            operation: operation,
            discovery: collector,
            latestMetadata: latestMetadata,
            providers: providers
        )
    }

    func read(
        entity: SourceEntity,
        scheme: String,
        path: String,
        chunkSize: Int
    ) -> AsyncThrowingStream<Data, Error> {
        guard let url = pending.withLock({ $0.removeValue(forKey: entity.ref.key) }) else {
            return AsyncThrowingStream { $0.finish(throwing: PhotoLibraryError.contentUnavailable(key: entity.ref.key)) }
        }
        return AsyncThrowingStream { continuation in
            let task = Task {
                do {
                    for try await chunk in FileByteSource.read(url, chunkSize: chunkSize) {
                        continuation.yield(chunk)
                    }
                    Self.cleanup(url)
                    continuation.finish()
                } catch {
                    Self.cleanup(url)
                    continuation.finish(throwing: error)
                }
            }
            continuation.onTermination = { _ in task.cancel() }
        }
    }

    func collector(
        targetMetadata: DatasetMetadata,
        keep: @escaping @Sendable (String, FilesystemMetadata.EntityState) -> Bool,
        destination: TargetEntity.Destination,
        providers: RecoveryProviders
    ) -> any RecoveryCollector {
        RecoveryCollectorImpl(
            scheme: scheme,
            targetMetadata: targetMetadata,
            keep: keep,
            destination: destination,
            clients: providers.clients
        )
    }

    func prepare(entity: TargetEntity, scheme: String, path: String, providers: RecoveryProviders) throws {}

    func write(
        entity: TargetEntity,
        scheme: String,
        path: String,
        content: AsyncThrowingStream<Data, Error>,
        providers: RecoveryProviders
    ) async throws {
        guard library.hasWriteAccess() else {
            throw PhotoLibraryPermissionMissing(scheme: scheme)
        }
        guard case .library(let existing) = entity.existingMetadata else {
            throw InvalidArgumentError("Expected library metadata for [\(path)]")
        }

        let attributes = PhotoAttributes.decoded(from: existing.attributes)
        let filename = Self.filename(fromPath: path)
        let album = PhotoAlbumSelector.album(forPath: path)
        let mediaType = attributes?.mediaType ?? .image
        let favorite = attributes?.favorite ?? false

        let temp = try Self.makeTemporaryFile(filename: filename)
        defer { Self.cleanup(temp) }
        try await Self.drain(content, to: temp)

        for candidate in await library.candidates(filename: filename, in: album) {
            if candidate.byteSize > 0 && candidate.byteSize != existing.size { continue }
            let candidateTemp = try Self.makeTemporaryFile(filename: filename)
            defer { Self.cleanup(candidateTemp) }
            try await library.materialize(candidate, to: candidateTemp)
            let candidateChecksum = try await providers.checksum.calculate(file: candidateTemp)
            if candidateChecksum == existing.checksum { return }
        }

        try await library.createAsset(
            from: temp,
            filename: filename,
            mediaType: mediaType,
            favorite: favorite,
            album: album
        )
    }

    func applyMetadata(entity: TargetEntity, scheme: String, path: String, providers: RecoveryProviders) async throws {}

    fileprivate func resolveSelection(
        _ collector: Backup.EntityDiscovery.Collector
    ) async -> [(album: PhotoAlbum, asset: PhotoAsset)] {
        switch collector {
        case .withRules(let rules): await selectionFromRules(rules)
        case .withState(let state): await selectionFromState(state)
        case .withEntities: []
        }
    }

    fileprivate func sourceEntity(
        album: PhotoAlbum,
        asset: PhotoAsset,
        latestMetadata: DatasetMetadata?,
        providers: BackupProviders
    ) async throws -> SourceEntity {
        let ref = Self.ref(scheme: scheme, album: album, filename: asset.originalFilename)
        let existing = try await latestMetadata?.collect(entity: ref.key, clients: providers.clients)
        let existingLibrary = Self.library(from: existing)
        let attributes = try PhotoAttributes(
            localIdentifier: asset.localIdentifier,
            favorite: asset.isFavorite,
            mediaType: asset.mediaType,
            modificationMillis: asset.modificationMillis,
            albums: asset.albums
        ).encoded()

        if let existingLibrary,
           let millis = asset.modificationMillis,
           PhotoAttributes.decoded(from: existingLibrary.attributes)?.modificationMillis == millis {
            return try Self.reusedEntity(ref: ref, existing: existingLibrary, attributes: attributes)
        }

        return try await materializedEntity(
            ref: ref,
            asset: asset,
            existing: existingLibrary,
            attributes: attributes,
            providers: providers
        )
    }

    private static func reusedEntity(
        ref: EntityRef,
        existing: EntityMetadata.Library,
        attributes: Data
    ) throws -> SourceEntity {
        let current = EntityMetadata.Library(
            path: ref.key,
            created: existing.created,
            updated: existing.updated,
            size: existing.size,
            checksum: existing.checksum,
            crates: existing.crates,
            compression: existing.compression,
            attributes: attributes
        )
        return try SourceEntity(ref: ref, existingMetadata: .library(existing), currentMetadata: .library(current))
    }

    private func materializedEntity(
        ref: EntityRef,
        asset: PhotoAsset,
        existing: EntityMetadata.Library?,
        attributes: Data,
        providers: BackupProviders
    ) async throws -> SourceEntity {
        let temp = try Self.makeTemporaryFile(filename: asset.originalFilename)
        try await library.materialize(asset, to: temp)
        let checksum = try await providers.checksum.calculate(file: temp)
        let size = Self.fileSize(temp)
        pending.withLock { $0[ref.key] = temp }

        let now = Date()
        let reusable = existing.flatMap { $0.checksum == checksum ? $0 : nil }
        let current = EntityMetadata.Library(
            path: ref.key,
            created: existing?.created ?? now,
            updated: reusable?.updated ?? now,
            size: size,
            checksum: checksum,
            crates: reusable?.crates ?? [:],
            compression: providers.compression.algorithmFor(entity: URL(fileURLWithPath: asset.originalFilename)),
            attributes: attributes
        )
        return try SourceEntity(ref: ref, existingMetadata: existing.map { .library($0) }, currentMetadata: .library(current))
    }

    private func selectionFromRules(_ rules: [Rule]) async -> [(album: PhotoAlbum, asset: PhotoAsset)] {
        let photoRules = rules.filter { SourceUri.scheme($0.source) == scheme }
        let includes = photoRules.filter { $0.operation == .include }
        let excludes = photoRules.filter { $0.operation == .exclude }

        var result: [(album: PhotoAlbum, asset: PhotoAsset)] = []
        var seen = Set<String>()
        for rule in includes {
            guard let album = PhotoAlbumSelector.parse(rule.source) else { continue }
            for asset in await library.assets(in: album)
            where Self.matches(rule.pattern, asset.originalFilename)
                && !Self.excluded(asset, album: album, rules: excludes) {
                let key = Self.ref(scheme: scheme, album: album, filename: asset.originalFilename).key
                if seen.insert(key).inserted { result.append((album, asset)) }
            }
        }
        return result
    }

    private func selectionFromState(_ state: BackupState) async -> [(album: PhotoAlbum, asset: PhotoAsset)] {
        var result: [(album: PhotoAlbum, asset: PhotoAsset)] = []
        var seen = Set<String>()
        for ref in state.remainingEntities() {
            guard case .library(let entityScheme, let path) = ref, entityScheme == scheme else { continue }
            let album = PhotoAlbumSelector.album(forPath: path)
            let filename = Self.filename(fromPath: path)
            guard let asset = await library.candidates(filename: filename, in: album).first else { continue }
            let key = Self.ref(scheme: scheme, album: album, filename: filename).key
            if seen.insert(key).inserted { result.append((album, asset)) }
        }
        return result
    }

    private static func excluded(_ asset: PhotoAsset, album: PhotoAlbum, rules: [Rule]) -> Bool {
        rules.contains { rule in
            guard let excludeAlbum = PhotoAlbumSelector.parse(rule.source) else { return false }
            let albumMatches = excludeAlbum == album || excludeAlbum == .all
            return albumMatches && matches(rule.pattern, asset.originalFilename)
        }
    }

    private static func matches(_ pattern: String, _ name: String) -> Bool {
        if pattern.isEmpty || pattern == "*" { return true }
        return NSPredicate(format: "SELF LIKE[c] %@", pattern).evaluate(with: name)
    }

    private static func ref(scheme: String, album: PhotoAlbum, filename: String) -> EntityRef {
        .library(scheme: scheme, path: "/\(album.pathSegment)/\(filename)")
    }

    private static func filename(fromPath path: String) -> String {
        path.split(separator: "/").last.map(String.init) ?? UUID().uuidString
    }

    private static func library(from metadata: EntityMetadata?) -> EntityMetadata.Library? {
        if case .library(let library) = metadata { return library }
        return nil
    }

    private static func makeTemporaryFile(filename: String) throws -> URL {
        let directory = FileManager.default.temporaryDirectory
            .appendingPathComponent("photos-\(UUID().uuidString)")
        try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
        let name = filename.isEmpty ? UUID().uuidString : filename
        return directory.appendingPathComponent(name)
    }

    private static func drain(_ content: AsyncThrowingStream<Data, Error>, to url: URL) async throws {
        if !FileManager.default.fileExists(atPath: url.path) {
            FileManager.default.createFile(atPath: url.path, contents: nil)
        }
        let handle = try FileHandle(forWritingTo: url)
        do {
            for try await chunk in content { try handle.write(contentsOf: chunk) }
            try handle.close()
        } catch {
            try? handle.close()
            throw error
        }
    }

    private static func fileSize(_ url: URL) -> Int64 {
        let values = try? url.resourceValues(forKeys: [.fileSizeKey])
        return Int64(values?.fileSize ?? 0)
    }

    private static func cleanup(_ url: URL) {
        try? FileManager.default.removeItem(at: url.deletingLastPathComponent())
    }

    private struct Collector: BackupCollector {
        let kind: PhotosEntityKind
        let operation: OperationId
        let discovery: Backup.EntityDiscovery.Collector
        let latestMetadata: DatasetMetadata?
        let providers: BackupProviders

        func collect() -> AsyncThrowingStream<SourceEntity, Error> {
            AsyncThrowingStream { continuation in
                let task = Task {
                    do {
                        kind.pending.withLock { $0.removeAll() }
                        for (album, asset) in await kind.resolveSelection(discovery) {
                            try Task.checkCancellation()
                            let entity = try await kind.sourceEntity(
                                album: album,
                                asset: asset,
                                latestMetadata: latestMetadata,
                                providers: providers
                            )
                            await providers.track.entityDiscovered(operation: operation, entity: entity.ref)
                            continuation.yield(entity)
                        }
                        continuation.finish()
                    } catch {
                        continuation.finish(throwing: error)
                    }
                }
                continuation.onTermination = { _ in task.cancel() }
            }
        }
    }

    private struct EmptyBackupCollector: BackupCollector {
        func collect() -> AsyncThrowingStream<SourceEntity, Error> {
            AsyncThrowingStream { $0.finish() }
        }
    }

    private struct RecoveryCollectorImpl: RecoveryCollector {
        let scheme: String
        let targetMetadata: DatasetMetadata
        let keep: @Sendable (String, FilesystemMetadata.EntityState) -> Bool
        let destination: TargetEntity.Destination
        let clients: any Clients

        func collect() -> AsyncThrowingStream<TargetEntity, Error> {
            AsyncThrowingStream { continuation in
                let task = Task {
                    do {
                        let kept = targetMetadata.filesystem.collect { entity, state in
                            SourceUri.scheme(entity) == scheme && keep(entity, state) ? entity : nil
                        }
                        for entity in kept {
                            let existing = try await targetMetadata.require(entity: entity, clients: clients)
                            let target = try TargetEntity(
                                ref: EntityRef.default(key: entity),
                                destination: destination,
                                existingMetadata: existing,
                                currentMetadata: nil
                            )
                            continuation.yield(target)
                        }
                        continuation.finish()
                    } catch {
                        continuation.finish(throwing: error)
                    }
                }
                continuation.onTermination = { _ in task.cancel() }
            }
        }
    }
}
