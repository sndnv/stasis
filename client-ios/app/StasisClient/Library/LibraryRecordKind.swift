import Foundation
import StasisClientLib
import Synchronization

final class LibraryRecordKind<Source: LibraryRecordSource>:
    BackupLibraryKind, RecoveryLibraryKind, LibraryRecordPreviewing, Sendable {
    let scheme: String

    private let source: Source
    private let pending = Mutex<[String: Data]>([:])

    init(source: Source) {
        self.source = source
        self.scheme = source.scheme
    }

    func preview(bytes: Data) throws -> EntityPreview {
        source.describe(try JSONDecoder().decode(Source.Record.self, from: bytes))
    }

    func export(bytes: Data) throws -> ExportedContent {
        try source.export(try JSONDecoder().decode(Source.Record.self, from: bytes))
    }

    func collector(
        operation: OperationId,
        collector: Backup.EntityDiscovery.Collector,
        latestMetadata: DatasetMetadata?,
        providers: BackupProviders
    ) async throws -> any BackupCollector {
        guard source.hasReadAccess() else {
            await providers.track.failureEncountered(
                operation: operation,
                failure: LibraryPermissionMissing(scheme: scheme)
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
        guard let content = pending.withLock({ $0.removeValue(forKey: entity.ref.key) }) else {
            return AsyncThrowingStream { $0.finish(throwing: LibraryContentUnavailable(key: entity.ref.key)) }
        }
        return AsyncThrowingStream { continuation in
            continuation.yield(content)
            continuation.finish()
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
        guard source.hasWriteAccess() else {
            throw LibraryPermissionMissing(scheme: scheme)
        }
        var buffer = Data()
        for try await chunk in content {
            buffer.append(chunk)
        }
        let record = try JSONDecoder().decode(Source.Record.self, from: buffer)
        try await source.restore(record)
    }

    func applyMetadata(entity: TargetEntity, scheme: String, path: String, providers: RecoveryProviders) async throws {}

    fileprivate func resolveSelection(_ collector: Backup.EntityDiscovery.Collector) async throws -> [Source.Record] {
        switch collector {
        case .withRules(let rules): try await selectionFromRules(rules)
        case .withState(let state): try await selectionFromState(state)
        case .withEntities: []
        }
    }

    fileprivate func sourceEntity(
        record: Source.Record,
        latestMetadata: DatasetMetadata?,
        providers: BackupProviders
    ) async throws -> SourceEntity {
        let ref = EntityRef.library(scheme: scheme, path: "/\(source.id(record))")
        let content = try Self.encode(record)
        let attributes = try Self.encode(source.attributes(record))
        let checksum = try await Self.checksum(of: content, providers: providers)
        let existing = Self.library(from: try await latestMetadata?.collect(entity: ref.key, clients: providers.clients))
        let reusable = existing.flatMap { $0.checksum == checksum ? $0 : nil }
        let now = Date()
        let current = EntityMetadata.Library(
            path: ref.key,
            created: existing?.created ?? now,
            updated: reusable?.updated ?? now,
            size: Int64(content.count),
            checksum: checksum,
            crates: reusable?.crates ?? [:],
            compression: providers.compression.defaultCompression.name,
            attributes: attributes
        )
        let entity = try SourceEntity(
            ref: ref,
            existingMetadata: existing.map { .library($0) },
            currentMetadata: .library(current)
        )
        if entity.hasContentChanged {
            pending.withLock { $0[ref.key] = content }
        }
        return entity
    }

    private func selectionFromRules(_ rules: [Rule]) async throws -> [Source.Record] {
        let schemeRules = rules.filter { SourceUri.scheme($0.source) == scheme }
        let includes = schemeRules.filter { $0.operation == .include }.map(\.pattern)
        let excludes = schemeRules.filter { $0.operation == .exclude }.map(\.pattern)
        guard !includes.isEmpty else { return [] }
        return try await source.list().filter { record in
            let name = source.displayName(record)
            return includes.contains { Self.matches($0, name) } && !excludes.contains { Self.matches($0, name) }
        }
    }

    private func selectionFromState(_ state: BackupState) async throws -> [Source.Record] {
        let remaining = Set(state.remainingEntities().compactMap { ref -> String? in
            guard case .library(let entityScheme, let path) = ref, entityScheme == scheme else { return nil }
            return String(path.drop { $0 == "/" })
        })
        return try await source.list().filter { remaining.contains(source.id($0)) }
    }

    private static func matches(_ pattern: String, _ name: String) -> Bool {
        if pattern.isEmpty || pattern == "*" { return true }
        return NSPredicate(format: "SELF LIKE[c] %@", pattern).evaluate(with: name)
    }

    private static func encode<Value: Encodable>(_ value: Value) throws -> Data {
        let encoder = JSONEncoder()
        encoder.outputFormatting = .sortedKeys
        return try encoder.encode(value)
    }

    private static func checksum(of content: Data, providers: BackupProviders) async throws -> Data {
        let temp = FileManager.default.temporaryDirectory.appendingPathComponent("library-\(UUID().uuidString)")
        FileManager.default.createFile(atPath: temp.path, contents: content)
        defer { try? FileManager.default.removeItem(at: temp) }
        return try await providers.checksum.calculate(file: temp)
    }

    private static func library(from metadata: EntityMetadata?) -> EntityMetadata.Library? {
        if case .library(let library) = metadata { return library }
        return nil
    }

    private struct Collector: BackupCollector {
        let kind: LibraryRecordKind
        let operation: OperationId
        let discovery: Backup.EntityDiscovery.Collector
        let latestMetadata: DatasetMetadata?
        let providers: BackupProviders

        func collect() -> AsyncThrowingStream<SourceEntity, Error> {
            AsyncThrowingStream { continuation in
                let task = Task {
                    do {
                        kind.pending.withLock { $0.removeAll() }
                        for record in try await kind.resolveSelection(discovery) {
                            try Task.checkCancellation()
                            let entity = try await kind.sourceEntity(
                                record: record,
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
