import Foundation
@testable import StasisClientLib
import StasisClientLibTestSupport
import Testing

@Suite("MetadataPush stage")
struct MetadataPushTests {
    @Test("pushes dataset metadata")
    func pushesMetadata() async throws {
        let encryption = MockEncrypting()
        let api = MockServerApiEndpointClient()
        let core = MockServerCoreEndpointClient()
        let tracker = MockBackupTracker()

        let stage = Backup.MetadataPush(
            targetDataset: Fixtures.Datasets.default,
            deviceSecret: Fixtures.Secrets.default,
            providers: BackupProviders(
                checksum: Checksums.md5,
                staging: MockFileStaging(),
                compression: MockCompression(),
                encryptor: encryption,
                decryptor: MockDecrypting(),
                clients: StaticClients(api: api, core: core),
                track: tracker,
                analytics: NoOpAnalyticsCollector(),
                kinds: [BackupEntityKinds.filesystem]
            )
        )

        guard case .file(let fileOne) = Fixtures.Metadata.fileOne else {
            Issue.record("expected file metadata")
            return
        }

        let metadata = DatasetMetadata(
            contentChanged: [Fixtures.Metadata.fileOne.path: Fixtures.Metadata.fileOne],
            metadataChanged: [
                Fixtures.Metadata.fileTwo.path: Fixtures.Metadata.fileTwo,
                Fixtures.Metadata.fileThree.path: Fixtures.Metadata.fileThree
            ],
            filesystem: FilesystemMetadata(entities: [
                Fixtures.Metadata.fileOne.path: .new,
                Fixtures.Metadata.fileTwo.path: .updated,
                Fixtures.Metadata.fileThree.path: .updated
            ])
        )

        let upstream = AsyncThrowingStream<DatasetMetadata, Error> { continuation in
            continuation.yield(metadata)
            continuation.finish()
        }

        try await stage.push(operation: UUID(), metadata: upstream)

        #expect(encryption.calls.metadataSecret.count == 1)
        #expect(encryption.calls.fileSecret.isEmpty)

        let apiCalls = await api.calls
        #expect(apiCalls.entryCreated == 1)
        #expect(apiCalls.entryDeleted == 0)

        let pushCount = await core.pushCount
        #expect(pushCount == 1)

        let pulls = await core.pullCount
        #expect(pulls == 0)

        #expect(tracker.statistics[.metadataPushed] == 1)
        #expect(tracker.statistics[.failureEncountered] == 0)

        let pushed = await core.recordedPushes()
        #expect(pushed.count == 1)
        #expect(pushed[0].size > 0)

        let storedCrates = await core.storedCrates()
        #expect(storedCrates.count == 1)

        let request: CreateDatasetEntry? = await api.lastRequest()
        let entryRequest = try #require(request)
        #expect(entryRequest.definition == Fixtures.Datasets.default.id)
        #expect(!entryRequest.data.isEmpty)
        #expect(entryRequest.changes == 3)
        #expect(entryRequest.size == fileOne.size)
    }
}
