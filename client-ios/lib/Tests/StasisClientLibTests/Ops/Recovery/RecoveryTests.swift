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
                query: nil,
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
            query: nil,
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
            query: nil,
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
            analytics: NoOpAnalyticsCollector()
        )
        await #expect(throws: RecoveryDescriptorError.noEntryForDefinition(definition: definition)) {
            _ = try await Recovery.Descriptor.build(
                query: nil,
                destination: nil,
                collector: .withDefinition(definition: definition, until: nil),
                deviceSecret: Fixtures.Secrets.default,
                providers: providers
            )
        }
    }

    @Test("Descriptor.toRecoveryCollector produces a DefaultRecoveryCollector")
    func descriptorBuildsCollector() {
        let descriptor = Recovery.Descriptor(
            targetMetadata: .empty(),
            query: nil,
            destination: nil,
            deviceSecret: Fixtures.Secrets.default
        )
        let collector = descriptor.toRecoveryCollector(providers: makeProviders())
        #expect(collector is DefaultRecoveryCollector)
    }

    @Test("PathQuery matches absolute path regexes")
    func pathQueryMatchesAbsolute() throws {
        let path = "/tmp/a/b/c/test-file.json"
        for raw in ["/tmp/.*", "/.*/a/.*/c"] {
            let query = Recovery.PathQuery.forAbsolutePath(try NSRegularExpression(pattern: raw))
            #expect(query.matches(path: path), "Should match \(raw)")
        }
    }

    @Test("PathQuery matches file name regexes")
    func pathQueryMatchesFileName() throws {
        let path = "/tmp/a/b/c/test-file.json"
        for raw in ["test-file", "test-file\\.json", ".*"] {
            let query = Recovery.PathQuery.forFileName(try NSRegularExpression(pattern: raw))
            #expect(query.matches(path: path), "Should match \(raw)")
        }
        for raw in ["tmp", "/tmp$", "^/a/b/c.*"] {
            let query = Recovery.PathQuery.forFileName(try NSRegularExpression(pattern: raw))
            #expect(!query.matches(path: path), "Should not match \(raw)")
        }
    }

    @Test("PathQuery.parse picks absolute or file-name variant by '/' presence")
    func pathQueryParseSelectsVariant() throws {
        let absolute = try Recovery.PathQuery.parse("/tmp/some-file.txt")
        if case .forAbsolutePath = absolute {} else { Issue.record("expected forAbsolutePath") }

        let fileName = try Recovery.PathQuery.parse("some-file.txt")
        if case .forFileName = fileName {} else { Issue.record("expected forFileName") }
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
                query: nil,
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

    @Test("PathQuery.parse rejects invalid regex strings")
    func pathQueryParseFailsForInvalidRegex() {
        #expect(throws: (any Error).self) {
            _ = try Recovery.PathQuery.parse("[invalid")
        }
    }

    private func makeProviders(api: any ServerApiEndpointClient = MockServerApiEndpointClient()) -> RecoveryProviders {
        RecoveryProviders(
            checksum: Checksums.md5,
            staging: MockFileStaging(),
            compression: MockCompression(),
            decryptor: MockDecrypting(),
            clients: StaticClients(api: api, core: MockServerCoreEndpointClient()),
            track: MockRecoveryTracker(),
            analytics: NoOpAnalyticsCollector()
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
