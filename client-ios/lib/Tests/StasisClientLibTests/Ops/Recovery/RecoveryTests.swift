import Foundation
@testable import StasisClientLib
import StasisClientLibTestSupport
import Testing

@Suite("Recovery orchestrator")
struct RecoveryTests {
    @Test("rejects start when already started")
    func rejectsRepeatStart() async throws {
        let providers = makeProviders()
        let recovery = Recovery(
            descriptor: Recovery.Descriptor(
                targetMetadata: .empty(),
                entities: nil,
                sources: [.filesystem],
                destination: nil,
                deviceSecret: Fixtures.Secrets.default
            ),
            providers: providers
        )
        try await recovery.start()
        await #expect(throws: RecoveryError.alreadyStarted(id: recovery.id)) {
            try await recovery.start()
        }
    }

    @Test("Descriptor.build resolves entry by definition")
    func buildsDescriptorByDefinition() async throws {
        let api = MockServerApiEndpointClient()
        let providers = makeProviders(api: api)

        let descriptor = try await Recovery.Descriptor.build(
            entities: nil,
            sources: [.filesystem],
            destination: nil,
            collector: .withDefinition(definition: UUID(), until: nil),
            deviceSecret: Fixtures.Secrets.default,
            providers: providers
        )
        _ = descriptor

        let calls = await api.calls
        #expect(calls.entryLatestRetrieved == 1)
        #expect(calls.metadataWithEntryRetrieved == 1)
    }

    @Test("Descriptor.build resolves entry by entry id")
    func buildsDescriptorByEntry() async throws {
        let api = MockServerApiEndpointClient()
        let providers = makeProviders(api: api)

        _ = try await Recovery.Descriptor.build(
            entities: nil,
            sources: [.filesystem],
            destination: nil,
            collector: .withEntry(entry: UUID()),
            deviceSecret: Fixtures.Secrets.default,
            providers: providers
        )

        let calls = await api.calls
        #expect(calls.entryRetrieved == 1)
        #expect(calls.metadataWithEntryRetrieved == 1)
    }

    @Test("Descriptor.build fails when no entry is found for the definition")
    func buildsFailsWithoutEntry() async throws {
        let definition = UUID()
        let api = NoEntryApiClient(selfDevice: UUID())
        let providers = RecoveryProviders(
            checksum: Checksums.md5,
            staging: MockFileStaging(),
            compression: MockCompression(),
            decryptor: MockDecrypting(),
            clients: StaticClients(api: api, core: MockServerCoreEndpointClient()),
            track: MockRecoveryTracker(),
            analytics: NoOpAnalyticsCollector(),
            kinds: [RecoveryEntityKinds.filesystem]
        )
        await #expect(throws: RecoveryDescriptorError.noEntryForDefinition(definition: definition)) {
            _ = try await Recovery.Descriptor.build(
                entities: nil,
                sources: [.filesystem],
                destination: nil,
                collector: .withDefinition(definition: definition, until: nil),
                deviceSecret: Fixtures.Secrets.default,
                providers: providers
            )
        }
    }

    @Test("filesystem kind produces a FilesystemRecoveryCollector")
    func filesystemKindBuildsCollector() {
        let collector = RecoveryEntityKinds.filesystem.collector(
            targetMetadata: .empty(),
            keep: { _, _ in true },
            destination: .default,
            providers: makeProviders()
        )
        #expect(collector is FilesystemRecoveryCollector)
    }

    @Test("RecoverySourceKind.forEntity maps a scheme to a library, else filesystem")
    func forEntityMapsScheme() {
        #expect(RecoverySourceKind.forEntity("/tmp/a/b/c.txt") == .filesystem)
        #expect(RecoverySourceKind.forEntity("contacts:/1") == .library(scheme: "contacts"))
        #expect(RecoverySourceKind.forEntity("calendar:/e/2") == .library(scheme: "calendar"))
    }

    @Test("keep filters entities by the selected source kinds")
    func keepFiltersBySourceKind() {
        let descriptor = Recovery.Descriptor(
            targetMetadata: .empty(),
            entities: nil,
            sources: [.filesystem, .library(scheme: "contacts")],
            destination: nil,
            deviceSecret: Fixtures.Secrets.default
        )
        let keep = descriptor.keep()
        #expect(keep("/tmp/file.txt", .new))
        #expect(keep("contacts:/1", .new))
        #expect(!keep("calendar:/e/2", .new))
    }

    @Test("keep also restricts to an explicit entities set when present")
    func keepRestrictsToEntities() {
        let descriptor = Recovery.Descriptor(
            targetMetadata: .empty(),
            entities: ["/tmp/keep.txt"],
            sources: [.filesystem],
            destination: nil,
            deviceSecret: Fixtures.Secrets.default
        )
        let keep = descriptor.keep()
        #expect(keep("/tmp/keep.txt", .new))
        #expect(!keep("/tmp/other.txt", .new))
    }

    @Test("Destination converts to TargetEntity destination")
    func destinationConverts() {
        let dest = Recovery.Destination(path: "/tmp/test/path", keepStructure: false)
        let target = Optional.some(dest).toTargetEntityDestination()
        #expect(target == .directory(path: URL(fileURLWithPath: dest.path), keepDefaultStructure: false))

        let none: Recovery.Destination? = nil
        #expect(none.toTargetEntityDestination() == .default)
    }

    @Test("stop cancels a running recovery")
    func stopCancelsRunningRecovery() async throws {
        let recovery = Recovery(
            descriptor: Recovery.Descriptor(
                targetMetadata: .empty(),
                entities: nil,
                sources: [.filesystem],
                destination: nil,
                deviceSecret: Fixtures.Secrets.default
            ),
            providers: makeProviders()
        )
        let runTask = Task { try await recovery.start() }
        try await Task.sleep(for: .milliseconds(1))
        recovery.stop()
        _ = await runTask.result
    }

    private func makeProviders(api: any ServerApiEndpointClient = MockServerApiEndpointClient()) -> RecoveryProviders {
        RecoveryProviders(
            checksum: Checksums.md5,
            staging: MockFileStaging(),
            compression: MockCompression(),
            decryptor: MockDecrypting(),
            clients: StaticClients(api: api, core: MockServerCoreEndpointClient()),
            track: MockRecoveryTracker(),
            analytics: NoOpAnalyticsCollector(),
            kinds: [RecoveryEntityKinds.filesystem]
        )
    }
}

private actor NoEntryApiClient: ServerApiEndpointClient {
    nonisolated let selfDevice: DeviceId
    nonisolated let server: String = "no-entry-api"

    init(selfDevice: DeviceId) {
        self.selfDevice = selfDevice
    }

    func latestEntry(definition: DatasetDefinitionId, until: Date?) async throws -> DatasetEntry? { nil }

    func datasetDefinitions() async throws -> [DatasetDefinition] { [] }
    func datasetDefinition(definition: DatasetDefinitionId) async throws -> DatasetDefinition {
        throw EndpointFailure(message: "not implemented")
    }
    func createDatasetDefinition(request: CreateDatasetDefinition) async throws -> CreatedDatasetDefinition {
        throw EndpointFailure(message: "not implemented")
    }
    func updateDatasetDefinition(definition: DatasetDefinitionId, request: UpdateDatasetDefinition) async throws {}
    func deleteDatasetDefinition(definition: DatasetDefinitionId) async throws {}
    func datasetEntries(definition: DatasetDefinitionId) async throws -> [DatasetEntry] { [] }
    func datasetEntry(entry: DatasetEntryId) async throws -> DatasetEntry {
        throw EndpointFailure(message: "not implemented")
    }
    func createDatasetEntry(request: CreateDatasetEntry) async throws -> CreatedDatasetEntry {
        throw EndpointFailure(message: "not implemented")
    }
    func deleteDatasetEntry(entry: DatasetEntryId) async throws {}
    func publicSchedules() async throws -> [Schedule] { [] }
    func publicSchedule(schedule: ScheduleId) async throws -> Schedule {
        throw EndpointFailure(message: "not implemented")
    }
    func datasetMetadata(entry: DatasetEntryId) async throws -> DatasetMetadata { .empty() }
    func datasetMetadata(entry: DatasetEntry) async throws -> DatasetMetadata { .empty() }
    func user() async throws -> User { throw EndpointFailure(message: "not implemented") }
    func resetUserSalt() async throws -> UpdatedUserSalt { throw EndpointFailure(message: "not implemented") }
    func resetUserPassword(request: ResetUserPassword) async throws {}
    func device() async throws -> Device { throw EndpointFailure(message: "not implemented") }
    func pushDeviceKey(key: Data) async throws {}
    func pullDeviceKey() async throws -> Data { Data() }
    func deviceKeyExists() async throws -> Bool { false }
    func ping() async throws -> Ping { Ping(id: UUID()) }
    func commands(lastSequenceId: Int64?) async throws -> [CommandAsJson] { [] }
    func sendAnalyticsEntry(_ entry: AnalyticsEntry) async throws {}
}
