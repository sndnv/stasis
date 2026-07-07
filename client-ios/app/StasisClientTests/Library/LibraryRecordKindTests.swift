import Foundation
@testable import StasisClient
import StasisClientLib
import Testing

@Suite("LibraryRecordKind")
struct LibraryRecordKindTests {
    private let idA = "BA280C63-2E91-4601-A25E-B0FAF3899E49"
    private let idB = "4ABBC585-8E9A-4C2A-83F2-92EEFB7D2234"

    private func record(_ id: String, name: String) -> FakeLibraryRecordSource.Record {
        FakeLibraryRecordSource.Record(id: id, name: name, payload: "test a")
    }

    private func includeRule(_ pattern: String) -> Rule {
        Rule(id: 1, operation: .include, source: "test:/", pattern: pattern, definition: nil)
    }

    @Test("backs up matched records and streams the record JSON on read")
    func backsUpAndStreamsContent() async throws {
        let recordA = record(idA, name: "test")
        let source = FakeLibraryRecordSource(scheme: "test", records: [recordA], readAccess: true, writeAccess: true)
        let kind = LibraryRecordKind(source: source)
        let tracker = RecordingBackupTracker()

        let collector = try await kind.collector(
            operation: UUID(),
            collector: .withRules([includeRule("*")]),
            latestMetadata: nil,
            providers: backupProviders(tracker: tracker)
        )

        let entities = try await collect(collector.collect())
        #expect(entities.count == 1)
        let entity = try #require(entities.first)
        #expect(entity.ref.key == "test:/\(idA)")
        #expect(tracker.discovered.map(\.key) == ["test:/\(idA)"])

        let content = try await collectData(kind.read(entity: entity, scheme: "test", path: "/\(idA)", chunkSize: 8))
        #expect(entity.currentMetadata.content?.size == Int64(content.count))
        let decoded = try JSONDecoder().decode(FakeLibraryRecordSource.Record.self, from: content)
        #expect(decoded == recordA)

        let attributes = try #require(libraryMetadata(entity.currentMetadata)?.attributes)
        #expect(try JSONDecoder().decode([String: String].self, from: attributes) == ["name": "test"])

        await #expect(throws: LibraryContentUnavailable(key: entity.ref.key)) {
            _ = try await collectData(kind.read(entity: entity, scheme: "test", path: "/\(idA)", chunkSize: 8))
        }
    }

    @Test("excludes records that match an exclude rule")
    func excludesMatchingRecords() async throws {
        let source = FakeLibraryRecordSource(
            scheme: "test",
            records: [record(idA, name: "test"), record(idB, name: "skip")],
            readAccess: true,
            writeAccess: true
        )
        let kind = LibraryRecordKind(source: source)

        let collector = try await kind.collector(
            operation: UUID(),
            collector: .withRules([
                includeRule("*"),
                Rule(id: 2, operation: .exclude, source: "test:/", pattern: "skip", definition: nil)
            ]),
            latestMetadata: nil,
            providers: backupProviders(tracker: RecordingBackupTracker())
        )

        let entities = try await collect(collector.collect())
        #expect(entities.map(\.ref.key) == ["test:/\(idA)"])
    }

    @Test("selects only remaining records when resuming from state")
    func selectsRemainingFromState() async throws {
        let source = FakeLibraryRecordSource(
            scheme: "test",
            records: [record(idA, name: "test"), record(idB, name: "test a")],
            readAccess: true,
            writeAccess: true
        )
        let kind = LibraryRecordKind(source: source)

        var state = BackupState.start(operation: UUID(), definition: UUID())
        state = state.entityDiscovered(entity: .library(scheme: "test", path: "/\(idA)"))
        state = state.entityDiscovered(entity: .filesystem(URL(fileURLWithPath: "/tmp/test")))

        let collector = try await kind.collector(
            operation: UUID(),
            collector: .withState(state),
            latestMetadata: nil,
            providers: backupProviders(tracker: RecordingBackupTracker())
        )

        let entities = try await collect(collector.collect())
        #expect(entities.map(\.ref.key) == ["test:/\(idA)"])
    }

    @Test("records a failure and collects nothing without read access")
    func skipsWithoutReadAccess() async throws {
        let source = FakeLibraryRecordSource(
            scheme: "test",
            records: [record(idA, name: "test")],
            readAccess: false,
            writeAccess: true
        )
        let kind = LibraryRecordKind(source: source)
        let tracker = RecordingBackupTracker()

        let collector = try await kind.collector(
            operation: UUID(),
            collector: .withRules([includeRule("*")]),
            latestMetadata: nil,
            providers: backupProviders(tracker: tracker)
        )

        let entities = try await collect(collector.collect())
        #expect(entities.isEmpty)
        #expect(tracker.failures == 1)
    }

    @Test("restores a record by decoding the streamed JSON")
    func restoresDecodedRecord() async throws {
        let source = FakeLibraryRecordSource(scheme: "test", records: [], readAccess: true, writeAccess: true)
        let kind = LibraryRecordKind(source: source)
        let recordA = record(idA, name: "test")
        let content = try JSONEncoder().encode(recordA)

        try await kind.write(
            entity: try libraryTarget(key: "test:/\(idA)", size: content.count),
            scheme: "test",
            path: "/\(idA)",
            content: makeStream(content),
            providers: recoveryProviders()
        )

        #expect(source.restored == [recordA])
    }

    @Test("throws when restoring without write access")
    func failsRestoreWithoutWriteAccess() async throws {
        let source = FakeLibraryRecordSource(scheme: "test", records: [], readAccess: true, writeAccess: false)
        let kind = LibraryRecordKind(source: source)
        let content = try JSONEncoder().encode(record(idA, name: "test"))

        await #expect(throws: LibraryPermissionMissing(scheme: "test")) {
            try await kind.write(
                entity: try libraryTarget(key: "test:/\(idA)", size: content.count),
                scheme: "test",
                path: "/\(idA)",
                content: makeStream(content),
                providers: recoveryProviders()
            )
        }
    }

    @Test("recovery collector keeps only entities for its scheme")
    func recoveryCollectorFiltersByScheme() async throws {
        let source = FakeLibraryRecordSource(scheme: "test", records: [], readAccess: true, writeAccess: true)
        let kind = LibraryRecordKind(source: source)

        let key = "test:/\(idA)"
        let existing = EntityMetadata.Library(
            path: key,
            created: Date(timeIntervalSince1970: 1),
            updated: Date(timeIntervalSince1970: 1),
            size: 1,
            checksum: Data("test".utf8),
            crates: [:],
            compression: "identity",
            attributes: Data()
        )
        let targetMetadata = DatasetMetadata(
            contentChanged: [key: .library(existing)],
            metadataChanged: [:],
            filesystem: FilesystemMetadata(changes: [key, "photos:/\(idB)", "/plain/test"])
        )

        let collector = kind.collector(
            targetMetadata: targetMetadata,
            keep: { _, _ in true },
            destination: .default,
            providers: recoveryProviders()
        )

        let targets = try await collect(collector.collect())
        #expect(targets.map(\.ref.key) == [key])
    }

    @Test("preview decodes the record and delegates to the source describe")
    func previewDelegates() throws {
        let source = FakeLibraryRecordSource(scheme: "test", records: [], readAccess: true, writeAccess: true)
        let kind = LibraryRecordKind(source: source)
        let bytes = try JSONEncoder().encode(record(idA, name: "test"))

        let preview = try kind.preview(bytes: bytes)

        #expect(preview.sections.first?.fields.contains(EntityPreview.Field(label: "Name", value: "test")) == true)
    }

    @Test("export decodes the record and delegates to the source export")
    func exportDelegates() throws {
        let source = FakeLibraryRecordSource(scheme: "test", records: [], readAccess: true, writeAccess: true)
        let kind = LibraryRecordKind(source: source)
        let bytes = try JSONEncoder().encode(record(idA, name: "test"))

        let exported = try kind.export(bytes: bytes)

        #expect(exported.fileExtension == "txt")
        #expect(exported.bytes == Data("test a".utf8))
    }

    private func libraryTarget(key: String, size: Int) throws -> TargetEntity {
        let existing = EntityMetadata.Library(
            path: key,
            created: Date(timeIntervalSince1970: 1),
            updated: Date(timeIntervalSince1970: 1),
            size: Int64(size),
            checksum: Data(),
            crates: [:],
            compression: "identity",
            attributes: Data()
        )
        return try TargetEntity(
            ref: EntityRef.default(key: key),
            destination: .default,
            existingMetadata: .library(existing),
            currentMetadata: nil
        )
    }

    private func backupProviders(tracker: RecordingBackupTracker) -> BackupProviders {
        BackupProviders(
            checksum: Checksums.sha256,
            staging: DefaultFileStaging(storeDirectory: nil, prefix: "", suffix: ""),
            compression: Compressions.create(defaultCompression: Identity.shared, disabledExtensions: []),
            encryptor: Aes.shared,
            decryptor: Aes.shared,
            clients: StaticClients(api: MockServerApiEndpointClient(), core: MockServerCoreEndpointClient()),
            track: tracker,
            analytics: NoOpAnalyticsCollector(),
            kinds: []
        )
    }

    private func recoveryProviders() -> RecoveryProviders {
        RecoveryProviders(
            checksum: Checksums.sha256,
            staging: DefaultFileStaging(storeDirectory: nil, prefix: "", suffix: ""),
            compression: Compressions.create(defaultCompression: Identity.shared, disabledExtensions: []),
            decryptor: Aes.shared,
            clients: StaticClients(api: MockServerApiEndpointClient(), core: MockServerCoreEndpointClient()),
            track: RecordingRecoveryTracker(),
            analytics: NoOpAnalyticsCollector(),
            kinds: []
        )
    }

    private func makeStream(_ data: Data) -> AsyncThrowingStream<Data, Error> {
        AsyncThrowingStream { continuation in
            continuation.yield(data)
            continuation.finish()
        }
    }

    private func collect<T>(_ stream: AsyncThrowingStream<T, Error>) async throws -> [T] {
        var items: [T] = []
        for try await item in stream { items.append(item) }
        return items
    }

    private func collectData(_ stream: AsyncThrowingStream<Data, Error>) async throws -> Data {
        var data = Data()
        for try await chunk in stream { data.append(chunk) }
        return data
    }

    private func libraryMetadata(_ metadata: EntityMetadata) -> EntityMetadata.Library? {
        if case .library(let library) = metadata { return library }
        return nil
    }
}
