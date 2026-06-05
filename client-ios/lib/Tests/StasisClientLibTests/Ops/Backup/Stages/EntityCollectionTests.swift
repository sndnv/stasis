import Foundation
@testable import StasisClientLib
import StasisClientLibTestSupport
import Testing

@Suite("EntityCollection stage")
struct EntityCollectionTests {
    @Test("collects and filters files")
    func collectsAndFiltersFiles() async throws {
        let fileOne = Fixtures.Metadata.fileOne
        let fileTwo = Fixtures.Metadata.fileTwo
        let fileThree = Fixtures.Metadata.fileThree

        let sourceFile1 = try SourceEntity(
            path: URL(fileURLWithPath: fileOne.path),
            existingMetadata: nil,
            currentMetadata: fileOne
        )
        let sourceFile2 = try SourceEntity(
            path: URL(fileURLWithPath: fileTwo.path),
            existingMetadata: fileTwo,
            currentMetadata: fileTwo
        )
        let sourceFile3 = try SourceEntity(
            path: URL(fileURLWithPath: fileThree.path),
            existingMetadata: fileThree.withFileFlags(isHidden: true),
            currentMetadata: fileThree
        )

        let tracker = MockBackupTracker()
        let stage = Backup.EntityCollection(
            targetDataset: Fixtures.Datasets.default,
            providers: makeProviders(tracker: tracker)
        )

        let collector = MockBackupCollector(files: [sourceFile1, sourceFile2, sourceFile3])
        let collectorStream = AsyncThrowingStream<any BackupCollector, Error> { continuation in
            continuation.yield(collector)
            continuation.finish()
        }

        var collected: [SourceEntity] = []
        for try await entity in stage.collect(operation: UUID(), collectors: collectorStream) {
            collected.append(entity)
        }

        #expect(collected == [sourceFile1, sourceFile3])
        #expect(tracker.statistics[.entityExamined] == 3)
        #expect(tracker.statistics[.entitySkipped] == 1)
        #expect(tracker.statistics[.entityCollected] == 2)
        #expect(tracker.statistics[.started] == 0)
        #expect(tracker.statistics[.failureEncountered] == 0)
    }

    private func makeProviders(tracker: any BackupTracker) -> BackupProviders {
        BackupProviders(
            checksum: Checksums.md5,
            staging: MockFileStaging(),
            compression: MockCompression(),
            encryptor: MockEncrypting(),
            decryptor: MockDecrypting(),
            clients: StaticClients(
                api: MockServerApiEndpointClient(),
                core: MockServerCoreEndpointClient()
            ),
            track: tracker,
            analytics: NoOpAnalyticsCollector()
        )
    }
}
