import Foundation
@testable import StasisClientLib
import StasisClientLibTestSupport
import Testing

@Suite("Backup orchestrator")
struct BackupTests {
    @Test("runs the full pipeline for a configured file collection")
    func runsPipeline() async throws {
        let sourceFile1 = OpsResources.url("source-file-1")
        let sourceFile2 = OpsResources.url("source-file-2")

        let api = MockServerApiEndpointClient(selfDevice: Fixtures.Datasets.default.device)
        let core = MockServerCoreEndpointClient()
        let tracker = MockBackupTracker()
        let encryption = MockEncrypting()

        let providers = BackupProviders(
            checksum: Checksums.md5,
            staging: MockFileStaging(),
            compression: MockCompression(),
            encryptor: encryption,
            decryptor: MockDecrypting(),
            clients: StaticClients(api: api, core: core),
            track: tracker,
            analytics: NoOpAnalyticsCollector()
        )

        let backup = Backup(
            descriptor: Backup.Descriptor(
                targetDataset: Fixtures.Datasets.default,
                latestEntry: nil,
                latestMetadata: nil,
                deviceSecret: Fixtures.Secrets.default,
                collector: .withEntities([sourceFile1, sourceFile2]),
                limits: Backup.Descriptor.Limits(maxPartSize: 16_384)
            ),
            providers: providers
        )

        try await backup.start()

        #expect(tracker.statistics[.started] == 1)
        #expect(tracker.statistics[.entityDiscovered] == 2)
        #expect(tracker.statistics[.entityExamined] == 2)
        #expect(tracker.statistics[.entityCollected] == 2)
        #expect(tracker.statistics[.entityProcessed] == 2)
        #expect(tracker.statistics[.metadataCollected] == 1)
        #expect(tracker.statistics[.metadataPushed] == 1)
        #expect(tracker.statistics[.completed] == 1)
        #expect(tracker.statistics[.failureEncountered] == 0)

        let calls = await api.calls
        #expect(calls.entryCreated == 1)

        let pushCount = await core.pushCount
        #expect(pushCount == 3) // 2 file crates + 1 metadata crate
    }

    @Test("rejects start when already started")
    func rejectsRepeatStart() async throws {
        let providers = makeProviders()
        let backup = Backup(
            descriptor: Backup.Descriptor(
                targetDataset: Fixtures.Datasets.default,
                latestEntry: nil,
                latestMetadata: nil,
                deviceSecret: Fixtures.Secrets.default,
                collector: .withEntities([]),
                limits: Backup.Descriptor.Limits(maxPartSize: 16_384)
            ),
            providers: providers
        )

        try await backup.start()
        await #expect(throws: BackupError.alreadyStarted(id: backup.id)) {
            try await backup.start()
        }
    }

    @Test("derives id from existing state when resuming")
    func usesStateOperationId() async throws {
        let stateOperation = UUID()
        let state = BackupState(
            operation: stateOperation,
            definition: Fixtures.Datasets.default.id,
            started: Date(),
            entities: .empty(),
            metadataCollected: nil,
            metadataPushed: nil,
            failures: [],
            completed: nil
        )
        let backup = Backup(
            descriptor: Backup.Descriptor(
                targetDataset: Fixtures.Datasets.default,
                latestEntry: nil,
                latestMetadata: nil,
                deviceSecret: Fixtures.Secrets.default,
                collector: .withState(state),
                limits: Backup.Descriptor.Limits(maxPartSize: 16_384)
            ),
            providers: makeProviders()
        )
        #expect(backup.id == stateOperation)
    }

    @Test("builds a descriptor from server state")
    func buildsDescriptor() async throws {
        let api = MockServerApiEndpointClient()
        let providers = BackupProviders(
            checksum: Checksums.md5,
            staging: MockFileStaging(),
            compression: MockCompression(),
            encryptor: MockEncrypting(),
            decryptor: MockDecrypting(),
            clients: StaticClients(api: api, core: MockServerCoreEndpointClient()),
            track: MockBackupTracker(),
            analytics: NoOpAnalyticsCollector()
        )

        let descriptor = try await Backup.Descriptor.build(
            definition: UUID(),
            collector: .withEntities([]),
            deviceSecret: Fixtures.Secrets.default,
            limits: Backup.Descriptor.Limits(maxPartSize: 16_384),
            providers: providers
        )

        #expect(descriptor.latestEntry != nil)

        let calls = await api.calls
        #expect(calls.definitionRetrieved == 1)
        #expect(calls.entryLatestRetrieved == 1)
        #expect(calls.metadataWithEntryRetrieved == 1)
    }

    @Test("Descriptor.Collector converts to EntityDiscovery.Collector")
    func descriptorCollectorConverts() {
        let rules: [Rule] = []
        let entities: [URL] = []
        let state = Fixtures.State.backupOneState

        let withRules = Backup.Descriptor.Collector.withRules(rules).asDiscoveryCollector()
        if case .withRules(let outRules) = withRules {
            #expect(outRules == rules)
        } else {
            Issue.record("expected withRules")
        }

        let withEntities = Backup.Descriptor.Collector.withEntities(entities).asDiscoveryCollector()
        if case .withEntities(let outEntities) = withEntities {
            #expect(outEntities == entities)
        } else {
            Issue.record("expected withEntities")
        }

        let withState = Backup.Descriptor.Collector.withState(state).asDiscoveryCollector()
        if case .withState(let outState) = withState {
            #expect(outState == state)
        } else {
            Issue.record("expected withState")
        }
    }

    @Test("Descriptor.Collector exposes existing state when available")
    func descriptorCollectorExistingState() {
        #expect(Backup.Descriptor.Collector.withRules([]).existingState() == nil)
        #expect(Backup.Descriptor.Collector.withEntities([]).existingState() == nil)
        #expect(Backup.Descriptor.Collector.withState(Fixtures.State.backupOneState).existingState() == Fixtures.State.backupOneState)
    }

    @Test("stop cancels a running backup")
    func stopCancelsRunningBackup() async throws {
        let backup = Backup(
            descriptor: Backup.Descriptor(
                targetDataset: Fixtures.Datasets.default,
                latestEntry: nil,
                latestMetadata: nil,
                deviceSecret: Fixtures.Secrets.default,
                collector: .withEntities([OpsResources.url("source-file-1")]),
                limits: Backup.Descriptor.Limits(maxPartSize: 16_384)
            ),
            providers: makeProviders()
        )

        let runTask = Task { try await backup.start() }
        try await Task.sleep(for: .milliseconds(1))
        backup.stop()
        _ = await runTask.result
        // assertion: stop didn't crash, and the task finished (result resolved)
    }

    private func makeProviders() -> BackupProviders {
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
            track: MockBackupTracker(),
            analytics: NoOpAnalyticsCollector()
        )
    }
}
