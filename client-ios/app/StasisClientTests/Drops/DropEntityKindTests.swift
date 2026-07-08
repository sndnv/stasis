import Foundation
@testable import StasisClient
import StasisClientLib
import Testing

@Suite("DropEntityKind")
struct DropEntityKindTests {
    @Test("backs up a drop and streams its bytes on read")
    func backsUpAndStreams() async throws {
        let bytes = Data("test a".utf8)
        let inbox = makeInbox()
        let drop = try await inbox.store(filename: "test.txt", typeIdentifier: "public.plain-text", data: bytes)

        let tracker = RecordingBackupTracker()
        let kind = DropEntityKind(inbox: inbox)
        let collector = try await kind.collector(
            operation: UUID(),
            collector: .withRules([]),
            latestMetadata: nil,
            providers: backupProviders(tracker: tracker)
        )

        let entities = try await collect(collector.collect())
        let entity = try #require(entities.first)
        #expect(entity.ref.key == "drop:/\(drop.id)/test.txt")
        #expect(entity.currentMetadata.content?.size == Int64(bytes.count))
        #expect(tracker.discovered.map(\.key) == [entity.ref.key])

        let read = try await collectData(
            kind.read(entity: entity, scheme: "drop", path: "/\(drop.id)/test.txt", chunkSize: 4)
        )
        #expect(read == bytes)
    }

    @Test("enumerates every drop even with no matching rules")
    func ignoresRules() async throws {
        let inbox = makeInbox()
        _ = try await inbox.store(filename: "a.txt", typeIdentifier: nil, data: Data("test".utf8))
        _ = try await inbox.store(filename: "b.txt", typeIdentifier: nil, data: Data("test a".utf8))

        let kind = DropEntityKind(inbox: inbox)
        let collector = try await kind.collector(
            operation: UUID(),
            collector: .withRules([]),
            latestMetadata: nil,
            providers: backupProviders(tracker: RecordingBackupTracker())
        )

        let entities = try await collect(collector.collect())
        #expect(entities.count == 2)
    }

    @Test("reaps a drop already captured by the last backup and skips it")
    func reapsAlreadyCapturedDrop() async throws {
        let inbox = makeInbox()
        let captured = try await inbox.store(filename: "captured.txt", typeIdentifier: nil, data: Data("test".utf8))
        let fresh = try await inbox.store(filename: "fresh.txt", typeIdentifier: nil, data: Data("test a".utf8))
        let capturedKey = "drop:/\(captured.id)/captured.txt"
        let existing = EntityMetadata.Library(
            path: capturedKey,
            created: captured.createdAt,
            updated: captured.createdAt,
            size: 4,
            checksum: try await sha256(Data("test".utf8)),
            crates: [:],
            compression: "identity",
            attributes: try captured.encoded()
        )
        let latest = DatasetMetadata(
            contentChanged: [capturedKey: .library(existing)],
            metadataChanged: [:],
            filesystem: FilesystemMetadata(changes: [capturedKey])
        )

        let kind = DropEntityKind(inbox: inbox)
        let collector = try await kind.collector(
            operation: UUID(),
            collector: .withRules([]),
            latestMetadata: latest,
            providers: backupProviders(tracker: RecordingBackupTracker())
        )

        let entities = try await collect(collector.collect())
        #expect(entities.map(\.ref.key) == ["drop:/\(fresh.id)/fresh.txt"])
        #expect(inbox.list().map(\.id) == [fresh.id])
    }

    @Test("recovers a drop back into the inbox")
    func recoversToInbox() async throws {
        let bytes = Data("test a".utf8)
        let inbox = makeInbox()
        let metadata = DropMetadata(
            id: "2380B7C6-9C31-4E2F-9C4C-1F9B0A6C9E10",
            filename: "test.txt",
            size: Int64(bytes.count),
            typeIdentifier: "public.plain-text",
            createdAt: Date(timeIntervalSince1970: 1000)
        )
        let key = "drop:/\(metadata.id)/\(metadata.filename)"
        let existing = EntityMetadata.Library(
            path: key,
            created: metadata.createdAt,
            updated: metadata.createdAt,
            size: Int64(bytes.count),
            checksum: try await sha256(bytes),
            crates: [:],
            compression: "identity",
            attributes: try metadata.encoded()
        )
        let target = try TargetEntity(
            ref: EntityRef.default(key: key),
            destination: .default,
            existingMetadata: .library(existing),
            currentMetadata: nil
        )

        let kind = DropEntityKind(inbox: inbox)
        try await kind.write(
            entity: target,
            scheme: "drop",
            path: "/\(metadata.id)/\(metadata.filename)",
            content: makeStream(bytes),
            providers: recoveryProviders()
        )

        let listed = try #require(inbox.list().first)
        #expect(listed == metadata)
        #expect(try Data(contentsOf: inbox.contentURL(for: listed)) == bytes)
    }

    private func makeInbox() -> DropInbox {
        DropInbox(directory: FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString))
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

    private func sha256(_ data: Data) async throws -> Data {
        let url = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString)
        try data.write(to: url)
        defer { try? FileManager.default.removeItem(at: url) }
        return try await Checksums.sha256(file: url)
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
}
