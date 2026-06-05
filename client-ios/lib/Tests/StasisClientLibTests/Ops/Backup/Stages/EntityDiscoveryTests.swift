import Foundation
@testable import StasisClientLib
import StasisClientLibTestSupport
import Testing

@Suite("EntityDiscovery stage")
struct EntityDiscoveryTests {
    @Test("discovers files (based on rules)")
    func discoversBasedOnRules() async throws {
        let opsDirectory = OpsResources.url("").deletingLastPathComponent().appendingPathComponent("ops")
        let nestedDirectory = opsDirectory.appendingPathComponent("nested")

        let tracker = MockBackupTracker()

        let rules = [
            Rule(
                id: 0, operation: .include,
                directory: opsDirectory.path, pattern: "source-file-*", definition: nil
            ),
            Rule(
                id: 1, operation: .include,
                directory: nestedDirectory.path, pattern: "source-file-*", definition: nil
            )
        ]
        let stage = Backup.EntityDiscovery(
            collector: .withRules(rules),
            latestMetadata: .empty(),
            providers: makeProviders(tracker: tracker)
        )

        let collector = try #require(try await collectFirst(stage.discover(operation: UUID())))
        var entities: [SourceEntity] = []
        for try await entity in collector.collect() {
            entities.append(entity)
        }

        #expect(entities.count == 7)
        #expect(tracker.statistics[.entityDiscovered] == 7)
        #expect(tracker.statistics[.specificationProcessed] == 1)
        #expect(tracker.statistics[.entityExamined] == 0)
        #expect(tracker.statistics[.failureEncountered] == 0)
    }

    @Test("discovers files (based on entities)")
    func discoversBasedOnEntities() async throws {
        let sourceFile1 = OpsResources.url("source-file-1")
        let sourceFile2 = OpsResources.url("source-file-2")
        let invalidFile = URL(fileURLWithPath: "/ops/invalid-file")

        let tracker = MockBackupTracker()

        let stage = Backup.EntityDiscovery(
            collector: .withEntities([sourceFile1, sourceFile2, invalidFile]),
            latestMetadata: .empty(),
            providers: makeProviders(tracker: tracker)
        )

        let collector = try #require(try await collectFirst(stage.discover(operation: UUID())))
        var entities: [SourceEntity] = []
        for try await entity in collector.collect() {
            entities.append(entity)
        }

        #expect(entities.count == 2)
        #expect(tracker.statistics[.entityDiscovered] == 2)
        #expect(tracker.statistics[.specificationProcessed] == 0)
    }

    @Test("discovers files (based on backup state)")
    func discoversBasedOnState() async throws {
        let sourceFile1 = OpsResources.url("source-file-1")
        let sourceFile2 = OpsResources.url("source-file-2")
        let processedPath = URL(fileURLWithPath: Fixtures.Metadata.fileThree.path)

        let baseState = Fixtures.State.backupTwoState
        let state = BackupState(
            operation: baseState.operation,
            definition: baseState.definition,
            started: baseState.started,
            entities: BackupState.Entities(
                discovered: [sourceFile1, sourceFile2, processedPath],
                unmatched: [],
                examined: [],
                skipped: [],
                collected: [:],
                pending: [:],
                processed: [
                    processedPath: BackupState.ProcessedSourceEntity(
                        expectedParts: 1,
                        processedParts: 1,
                        metadata: .left(Fixtures.Metadata.fileThree)
                    )
                ],
                failed: [:]
            ),
            metadataCollected: nil,
            metadataPushed: nil,
            failures: [],
            completed: nil
        )

        let tracker = MockBackupTracker()
        let stage = Backup.EntityDiscovery(
            collector: .withState(state),
            latestMetadata: .empty(),
            providers: makeProviders(tracker: tracker)
        )

        let collector = try #require(try await collectFirst(stage.discover(operation: UUID())))
        var entities: [SourceEntity] = []
        for try await entity in collector.collect() {
            entities.append(entity)
        }

        #expect(entities.count == 2)
        #expect(tracker.statistics[.entityDiscovered] == 0)
        #expect(tracker.statistics[.specificationProcessed] == 0)
    }

    private func collectFirst(
        _ stream: AsyncThrowingStream<any BackupCollector, Error>
    ) async throws -> (any BackupCollector)? {
        for try await item in stream { return item }
        return nil
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
