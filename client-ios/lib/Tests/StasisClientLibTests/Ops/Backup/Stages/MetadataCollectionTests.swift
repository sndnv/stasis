import Foundation
@testable import StasisClientLib
import StasisClientLibTestSupport
import Testing

@Suite("MetadataCollection stage")
struct MetadataCollectionTests {
    @Test("collects dataset metadata (with previous metadata)")
    func collectsWithPrevious() async throws {
        let tracker = MockBackupTracker()

        let stage = Backup.MetadataCollection(
            latestEntry: Fixtures.Entries.default,
            latestMetadata: DatasetMetadata(
                contentChanged: [Fixtures.Metadata.fileOne.path: Fixtures.Metadata.fileOne],
                metadataChanged: [:],
                filesystem: FilesystemMetadata(changes: [Fixtures.Metadata.fileOne.path])
            ),
            providers: makeProviders(tracker: tracker)
        )

        let output = try await runCollect(stage: stage, inputs: [
            .right(Fixtures.Metadata.fileOne),
            .left(Fixtures.Metadata.fileTwo),
            .right(Fixtures.Metadata.fileThree)
        ], existingState: nil)

        #expect(output.count == 1)

        let expected = DatasetMetadata(
            contentChanged: [Fixtures.Metadata.fileTwo.path: Fixtures.Metadata.fileTwo],
            metadataChanged: [
                Fixtures.Metadata.fileOne.path: Fixtures.Metadata.fileOne,
                Fixtures.Metadata.fileThree.path: Fixtures.Metadata.fileThree
            ],
            filesystem: FilesystemMetadata(entities: [
                Fixtures.Metadata.fileOne.path: .updated,
                Fixtures.Metadata.fileTwo.path: .new,
                Fixtures.Metadata.fileThree.path: .new
            ])
        )
        #expect(output[0] == expected)
        #expect(tracker.statistics[.metadataCollected] == 1)
        #expect(tracker.statistics[.failureEncountered] == 0)
    }

    @Test("collects dataset metadata (without previous metadata)")
    func collectsWithoutPrevious() async throws {
        let tracker = MockBackupTracker()
        let stage = Backup.MetadataCollection(
            latestEntry: nil,
            latestMetadata: nil,
            providers: makeProviders(tracker: tracker)
        )

        let output = try await runCollect(stage: stage, inputs: [
            .right(Fixtures.Metadata.fileOne),
            .left(Fixtures.Metadata.fileTwo),
            .right(Fixtures.Metadata.fileThree)
        ], existingState: nil)

        #expect(output.count == 1)

        let expected = DatasetMetadata(
            contentChanged: [Fixtures.Metadata.fileTwo.path: Fixtures.Metadata.fileTwo],
            metadataChanged: [
                Fixtures.Metadata.fileOne.path: Fixtures.Metadata.fileOne,
                Fixtures.Metadata.fileThree.path: Fixtures.Metadata.fileThree
            ],
            filesystem: FilesystemMetadata(changes: [
                Fixtures.Metadata.fileOne.path,
                Fixtures.Metadata.fileTwo.path,
                Fixtures.Metadata.fileThree.path
            ])
        )
        #expect(output[0] == expected)
        #expect(tracker.statistics[.metadataCollected] == 1)
    }

    @Test("collects dataset metadata (with existing state)")
    func collectsWithExistingState() async throws {
        let tracker = MockBackupTracker()
        let stage = Backup.MetadataCollection(
            latestEntry: nil,
            latestMetadata: nil,
            providers: makeProviders(tracker: tracker)
        )

        let baseState = Fixtures.State.backupTwoState
        var entities = baseState.entities
        entities.processed = [
            .filesystem(URL(fileURLWithPath: Fixtures.Metadata.fileThree.path)): BackupState.ProcessedSourceEntity(
                expectedParts: 1,
                processedParts: 1,
                metadata: .right(Fixtures.Metadata.fileThree)
            )
        ]
        let existingState = BackupState(
            operation: baseState.operation,
            definition: baseState.definition,
            started: baseState.started,
            entities: entities,
            metadataCollected: baseState.metadataCollected,
            metadataPushed: baseState.metadataPushed,
            failures: baseState.failures,
            completed: baseState.completed
        )

        let output = try await runCollect(stage: stage, inputs: [
            .right(Fixtures.Metadata.fileOne),
            .left(Fixtures.Metadata.fileTwo)
        ], existingState: existingState)

        #expect(output.count == 1)

        let expected = DatasetMetadata(
            contentChanged: [Fixtures.Metadata.fileTwo.path: Fixtures.Metadata.fileTwo],
            metadataChanged: [
                Fixtures.Metadata.fileOne.path: Fixtures.Metadata.fileOne,
                Fixtures.Metadata.fileThree.path: Fixtures.Metadata.fileThree
            ],
            filesystem: FilesystemMetadata(changes: [
                Fixtures.Metadata.fileOne.path,
                Fixtures.Metadata.fileTwo.path,
                Fixtures.Metadata.fileThree.path
            ])
        )
        #expect(output[0] == expected)
        #expect(tracker.statistics[.metadataCollected] == 1)
    }

    private func runCollect(
        stage: Backup.MetadataCollection,
        inputs: [Either<EntityMetadata, EntityMetadata>],
        existingState: BackupState?
    ) async throws -> [DatasetMetadata] {
        let upstream = AsyncThrowingStream<Either<EntityMetadata, EntityMetadata>, Error> { continuation in
            for item in inputs {
                continuation.yield(item)
            }
            continuation.finish()
        }
        var output: [DatasetMetadata] = []
        for try await metadata in stage.collect(operation: UUID(), entities: upstream, existingState: existingState) {
            output.append(metadata)
        }
        return output
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
            analytics: NoOpAnalyticsCollector(),
            kinds: [BackupEntityKinds.filesystem]
        )
    }
}
