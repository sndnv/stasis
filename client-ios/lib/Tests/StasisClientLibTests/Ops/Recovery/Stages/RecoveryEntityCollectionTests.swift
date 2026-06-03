import Foundation
@testable import StasisClientLib
import Testing

@Suite("Recovery.EntityCollection stage")
struct RecoveryEntityCollectionTests {
    @Test("collects and filters files")
    func collectsAndFilters() async throws {
        let fileOne = Fixtures.Metadata.fileOne
        let fileTwo = Fixtures.Metadata.fileTwo
        let fileThree = Fixtures.Metadata.fileThree

        let target1 = try TargetEntity(
            path: URL(fileURLWithPath: fileOne.path),
            destination: .default,
            existingMetadata: fileOne,
            currentMetadata: nil
        )
        let target2 = try TargetEntity(
            path: URL(fileURLWithPath: fileTwo.path),
            destination: .default,
            existingMetadata: fileTwo,
            currentMetadata: fileTwo
        )
        let target3 = try TargetEntity(
            path: URL(fileURLWithPath: fileThree.path),
            destination: .default,
            existingMetadata: fileThree,
            currentMetadata: fileThree.withFileFlags(isHidden: true)
        )

        let tracker = MockRecoveryTracker()
        let stage = Recovery.EntityCollection(
            collector: MockRecoveryCollector(files: [target1, target2, target3]),
            providers: makeProviders(tracker: tracker)
        )

        var collected: [TargetEntity] = []
        for try await entity in stage.collect(operation: UUID()) {
            collected.append(entity)
        }

        #expect(collected == [target1, target3])
        #expect(tracker.statistics[.entityExamined] == 3)
        #expect(tracker.statistics[.entityCollected] == 2)
        #expect(tracker.statistics[.started] == 0)
        #expect(tracker.statistics[.failureEncountered] == 0)
    }

    private func makeProviders(tracker: any RecoveryTracker) -> RecoveryProviders {
        RecoveryProviders(
            checksum: Checksums.md5,
            staging: MockFileStaging(),
            compression: MockCompression(),
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
