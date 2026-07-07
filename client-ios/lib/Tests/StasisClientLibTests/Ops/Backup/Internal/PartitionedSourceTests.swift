import Foundation
@testable import StasisClientLib
import StasisClientLibTestSupport
import Testing

@Suite("PartitionedSource")
struct PartitionedSourceTests {
    private let maximumPartSize: Int64 = 3

    private func makeProviders(
        staging: MockFileStaging,
        encryption: MockEncrypting
    ) -> BackupProviders {
        BackupProviders(
            checksum: Checksums.md5,
            staging: staging,
            compression: MockCompression(),
            encryptor: encryption,
            decryptor: MockDecrypting(),
            clients: StaticClients(
                api: MockServerApiEndpointClient(),
                core: MockServerCoreEndpointClient()
            ),
            track: MockBackupTracker(),
            analytics: NoOpAnalyticsCollector(),
            kinds: [BackupEntityKinds.filesystem]
        )
    }

    @Test("partitions a data stream and stages each part")
    func partitionsAndStages() async throws {
        let staging = MockFileStaging()
        let encryption = MockEncrypting()
        let providers = makeProviders(staging: staging, encryption: encryption)

        let partsStaged = Counter()

        let source = PartitionedSource(
            source: makeDataStream(Data("original".utf8)),
            providers: providers,
            withPartSecret: { id in
                DeviceFileSecret(file: "/ops/source-file-\(id)", iv: Data(), key: Data())
            },
            onPartStaged: { partsStaged.increment() },
            maximumPartSize: maximumPartSize
        )

        let parts = try await source.partitionAndStage()
        defer {
            for part in parts { try? FileManager.default.removeItem(at: part.path) }
        }
        let entries = try parts.map { try Data(contentsOf: $0.path) }

        #expect(partsStaged.value == 3)
        #expect(entries.count == 3)
        #expect(entries[0] == Data([MockEncrypting.sentinel]) + Data("ori".utf8))
        #expect(entries[1] == Data([MockEncrypting.sentinel]) + Data("gin".utf8))
        #expect(entries[2] == Data([MockEncrypting.sentinel]) + Data("al".utf8))

        #expect(parts.map(\.file) == ["/ops/source-file-0", "/ops/source-file-1", "/ops/source-file-2"])

        let calls = encryption.calls
        #expect(calls.fileSecret.count == 3)
        #expect(calls.fileSecret.map(\.secret.file) == ["/ops/source-file-0", "/ops/source-file-1", "/ops/source-file-2"])
        #expect(calls.fileSecret.map(\.plaintext) == [Data("ori".utf8), Data("gin".utf8), Data("al".utf8)])
        #expect(calls.metadataSecret.isEmpty)

        let stats = staging.statistics
        #expect(stats.temporaryCreated == 3)
        #expect(stats.temporaryDiscarded == 0)
        #expect(stats.destaged == 0)
    }

    @Test("discards staged parts when partitioning fails")
    func discardsOnFailure() async {
        let staging = MockFileStaging()
        let encryption = MockEncrypting()
        let providers = makeProviders(staging: staging, encryption: encryption)

        let partsStaged = Counter()

        let failingStream = makeDataStream(
            [Data("abc".utf8), Data("abc".utf8)],
            thenThrow: TestFailure(message: "Test failure")
        )

        let source = PartitionedSource(
            source: failingStream,
            providers: providers,
            withPartSecret: { _ in
                DeviceFileSecret(file: "/ops/source-file-1", iv: Data(), key: Data())
            },
            onPartStaged: { partsStaged.increment() },
            maximumPartSize: maximumPartSize
        )

        await #expect(throws: TestFailure(message: "Test failure")) {
            try await source.partitionAndStage()
        }

        #expect(partsStaged.value == 2)

        let stats = staging.statistics
        #expect(stats.temporaryCreated == 2)
        #expect(stats.temporaryDiscarded == 2)
        #expect(stats.destaged == 0)
    }
}
