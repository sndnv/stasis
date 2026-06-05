import Foundation
@testable import StasisClientLib
import StasisClientLibTestSupport
import Testing

@Suite("DefaultOperationExecutor")
struct DefaultOperationExecutorTests {
    private let testRules: [Rule] = [
        Rule(id: 1, operation: .include, directory: "/home__/stasis", pattern: "**", definition: nil),
        Rule(id: 2, operation: .exclude, directory: "/home__/stasis", pattern: "**/*cache*/*", definition: nil),
        Rule(id: 3, operation: .exclude, directory: "/home__/stasis", pattern: "**/*log*/*", definition: nil)
    ]

    @Test("starts backups with rules")
    func startsBackupWithRules() async throws {
        let tracker = MockBackupTracker()
        let executor = makeExecutor(backupTracker: tracker)
        let completed = Flag()

        _ = await executor.startBackupWithRules(
            definition: Fixtures.Datasets.default.id,
            rules: testRules
        ) { _ in completed.set() }

        await waitUntil { completed.isSet }
        let stats = tracker.statistics
        #expect(stats[.started] == 1)
        #expect(stats[.specificationProcessed] == 1)
        #expect(stats[.entityDiscovered] == 0)
        #expect(stats[.metadataCollected] == 1)
        #expect(stats[.metadataPushed] == 1)
        #expect(stats[.completed] == 1)
        #expect(stats[.failureEncountered] == 0)
    }

    @Test("handles backup start failure with rules")
    func handlesBackupStartFailureWithRules() async throws {
        let api = MockServerApiEndpointClient(selfDevice: Fixtures.Datasets.default.device)
        await api.setDatasetDefinitionFailure(TestFailure())
        let executor = makeExecutor(api: api)
        let result = Box<Error?>(nil)

        _ = await executor.startBackupWithRules(
            definition: UUID(),
            rules: testRules
        ) { result.set($0) }

        await waitUntil { result.value != nil }
        #expect(result.value is TestFailure)
    }

    @Test("starts backups with entities")
    func startsBackupWithEntities() async throws {
        let tracker = MockBackupTracker()
        let executor = makeExecutor(backupTracker: tracker)
        let completed = Flag()

        _ = await executor.startBackupWithEntities(
            definition: Fixtures.Datasets.default.id,
            entities: []
        ) { _ in completed.set() }

        await waitUntil { completed.isSet }
        let stats = tracker.statistics
        #expect(stats[.started] == 1)
        #expect(stats[.specificationProcessed] == 0)
        #expect(stats[.metadataCollected] == 1)
        #expect(stats[.metadataPushed] == 1)
        #expect(stats[.completed] == 1)
        #expect(stats[.failureEncountered] == 0)
    }

    @Test("handles backup start failure with entities")
    func handlesBackupStartFailureWithEntities() async throws {
        let api = MockServerApiEndpointClient(selfDevice: Fixtures.Datasets.default.device)
        await api.setDatasetDefinitionFailure(TestFailure())
        let executor = makeExecutor(api: api)
        let result = Box<Error?>(nil)

        _ = await executor.startBackupWithEntities(
            definition: UUID(),
            entities: []
        ) { result.set($0) }

        await waitUntil { result.value != nil }
        #expect(result.value is TestFailure)
    }

    @Test("resumes backups with state")
    func resumesBackup() async throws {
        let operation = Operations.generateId()
        let tracker = StatefulBackupTracker(state:
            BackupState.start(operation: operation, definition: Fixtures.Datasets.default.id)
        )
        let executor = makeExecutor(backupTracker: tracker)
        let completed = Flag()

        _ = await executor.resumeBackup(operation: operation) { error in
            if error == nil { completed.set() }
        }

        await waitUntil { completed.isSet }
        let stats = tracker.statistics
        #expect(stats[.started] == 0) // resume does not re-emit started
        #expect(stats[.metadataCollected] == 1)
        #expect(stats[.metadataPushed] == 1)
        #expect(stats[.completed] == 1)
    }

    @Test("starts recoveries with definitions")
    func startsRecoveryWithDefinition() async throws {
        let tracker = MockRecoveryTracker()
        let executor = makeExecutor(recoveryTracker: tracker)
        let completed = Flag()

        _ = await executor.startRecoveryWithDefinition(
            definition: UUID(), until: nil, query: nil, destination: nil
        ) { _ in completed.set() }

        await waitUntil { completed.isSet }
        let stats = tracker.statistics
        #expect(stats[.started] == 1)
        #expect(stats[.entityExamined] == 0)
        #expect(stats[.entityCollected] == 0)
        #expect(stats[.metadataApplied] == 0)
        #expect(stats[.completed] == 1)
        #expect(stats[.failureEncountered] == 0)
    }

    @Test("handles recovery start failure with definitions")
    func handlesRecoveryStartFailureWithDefinition() async throws {
        let api = MockServerApiEndpointClient(selfDevice: Fixtures.Datasets.default.device)
        await api.setLatestEntryFailure(TestFailure())
        let executor = makeExecutor(api: api)
        let result = Box<Error?>(nil)

        _ = await executor.startRecoveryWithDefinition(
            definition: UUID(), until: nil, query: nil, destination: nil
        ) { result.set($0) }

        await waitUntil { result.value != nil }
        #expect(result.value is TestFailure)
    }

    @Test("starts recoveries with entries")
    func startsRecoveryWithEntry() async throws {
        let tracker = MockRecoveryTracker()
        let executor = makeExecutor(recoveryTracker: tracker)
        let completed = Flag()

        _ = await executor.startRecoveryWithEntry(
            entry: UUID(), query: nil, destination: nil
        ) { _ in completed.set() }

        await waitUntil { completed.isSet }
        let stats = tracker.statistics
        #expect(stats[.started] == 1)
        #expect(stats[.entityExamined] == 0)
        #expect(stats[.completed] == 1)
        #expect(stats[.failureEncountered] == 0)
    }

    @Test("handles recovery start failure with entries")
    func handlesRecoveryStartFailureWithEntry() async throws {
        let api = MockServerApiEndpointClient(selfDevice: Fixtures.Datasets.default.device)
        await api.setDatasetEntryFailure(TestFailure())
        let executor = makeExecutor(api: api)
        let result = Box<Error?>(nil)

        _ = await executor.startRecoveryWithEntry(
            entry: UUID(), query: nil, destination: nil
        ) { result.set($0) }

        await waitUntil { result.value != nil }
        #expect(result.value is TestFailure)
    }

    @Test("expirations are not yet implemented")
    func expirationsNotImplemented() async {
        let executor = makeExecutor()
        await #expect(throws: OperationExecutorError.notImplemented("Expiration is not supported")) {
            _ = try await executor.startExpiration { _ in }
        }
    }

    @Test("validations are not yet implemented")
    func validationsNotImplemented() async {
        let executor = makeExecutor()
        await #expect(throws: OperationExecutorError.notImplemented("Validation is not supported")) {
            _ = try await executor.startValidation { _ in }
        }
    }

    @Test("key rotations are not yet implemented")
    func keyRotationsNotImplemented() async {
        let executor = makeExecutor()
        await #expect(throws: OperationExecutorError.notImplemented("Key rotation is not supported")) {
            _ = try await executor.startKeyRotation { _ in }
        }
    }

    @Test("stops running operations")
    func stopsRunningOperations() async throws {
        let tracker = MockBackupTracker()
        let api = MockServerApiEndpointClient(
            selfDevice: Fixtures.Datasets.default.device,
            createDatasetEntryDelay: 0.5
        )
        let executor = makeExecutor(api: api, backupTracker: tracker)
        let completed = Flag()

        let operation = await executor.startBackupWithRules(
            definition: Fixtures.Datasets.default.id,
            rules: testRules
        ) { _ in completed.set() }

        // Wait for start() to have entered runPipeline (proven by tracker.started == 1)
        await waitUntil { tracker.statistics[.started] == 1 }

        try await executor.stop(operation: operation)
        await waitUntil { completed.isSet }
        #expect(completed.isSet)
    }

    @Test("fails to stop operations that are not running")
    func failsToStopUnknownOperation() async throws {
        let executor = makeExecutor()
        let unknown = Operations.generateId()

        await #expect(throws: OperationExecutorError.operationNotFound(unknown)) {
            try await executor.stop(operation: unknown)
        }
    }

    @Test("lists active and completed operations")
    func listsActiveAndCompleted() async throws {
        let tracker = MockBackupTracker()
        let api = MockServerApiEndpointClient(
            selfDevice: Fixtures.Datasets.default.device,
            createDatasetEntryDelay: 0.3
        )
        let executor = makeExecutor(api: api, backupTracker: tracker)
        let completed = Flag()

        #expect(await executor.active().isEmpty)
        #expect(await executor.completed().isEmpty)

        let backup = await executor.startBackupWithRules(
            definition: Fixtures.Datasets.default.id,
            rules: testRules
        ) { _ in completed.set() }

        await waitUntil { tracker.statistics[.started] == 1 }
        #expect(await executor.active()[backup] == .backup)

        await waitUntil { completed.isSet }
        #expect(await executor.active().isEmpty)
        #expect(await executor.completed()[backup] == .backup)
    }

    @Test("searches for operations by id")
    func searchesForOperations() async throws {
        let executor = makeExecutor()

        #expect(await executor.active().isEmpty)
        #expect(await executor.completed().isEmpty)
        #expect(await executor.find(operation: Operations.generateId()) == nil)

        let backup = await executor.startBackupWithEntities(
            definition: Fixtures.Datasets.default.id, entities: []
        ) { _ in }

        let recovery = await executor.startRecoveryWithEntry(
            entry: UUID(), query: nil, destination: nil
        ) { _ in }

        // find returns the type whether the op is active or completed
        await waitUntil {
            let foundBackup = await executor.find(operation: backup)
            let foundRecovery = await executor.find(operation: recovery)
            return foundBackup == .backup && foundRecovery == .recovery
        }
        #expect(await executor.find(operation: backup) == .backup)
        #expect(await executor.find(operation: recovery) == .recovery)
    }

    @Test("fails to start when one of the same type is already running")
    func failsToStartWhenSameTypeActive() async throws {
        let tracker = MockBackupTracker()
        let api = MockServerApiEndpointClient(
            selfDevice: Fixtures.Datasets.default.device,
            createDatasetEntryDelay: 0.5
        )
        let executor = makeExecutor(api: api, backupTracker: tracker)

        let firstId = await executor.startBackupWithRules(
            definition: Fixtures.Datasets.default.id, rules: testRules
        ) { _ in }

        await waitUntil { tracker.statistics[.started] == 1 }
        #expect(await executor.active()[firstId] == .backup)

        let result = Box<Error?>(nil)
        _ = await executor.startBackupWithRules(
            definition: Fixtures.Datasets.default.id, rules: testRules
        ) { result.set($0) }

        await waitUntil { result.value != nil }
        guard let err = result.value as? OperationExecutorError,
              case .operationAlreadyActive(let type, let existing) = err else {
            Issue.record("Expected operationAlreadyActive, got \(String(describing: result.value))")
            return
        }
        #expect(type == .backup)
        #expect(existing == firstId)
    }

    @Test("fails to start when there are restrictions")
    func failsToStartWhenRestricted() async throws {
        let expectedRestrictions: [OperationRestriction] = [.limitedNetwork]
        let executor = makeExecutor(restrictions: expectedRestrictions)
        let result = Box<Error?>(nil)

        _ = await executor.startBackupWithRules(
            definition: Fixtures.Datasets.default.id, rules: testRules
        ) { result.set($0) }

        await waitUntil { result.value != nil }
        guard let err = result.value as? OperationRestrictedFailure else {
            Issue.record("Expected OperationRestrictedFailure, got \(String(describing: result.value))")
            return
        }
        #expect(err.restrictions == expectedRestrictions)
    }

    @Test("fails to resume a backup that is already completed")
    func failsToResumeCompletedBackup() async throws {
        let operation = Operations.generateId()
        let tracker = StatefulBackupTracker(state:
            BackupState.start(operation: operation, definition: Fixtures.Datasets.default.id)
                .backupCompleted()
        )
        let executor = makeExecutor(backupTracker: tracker)
        let result = Box<Error?>(nil)

        _ = await executor.resumeBackup(operation: operation) { result.set($0) }

        await waitUntil { result.value != nil }
        #expect(result.value as? OperationExecutorError
            == .cannotResumeCompleted(operation: operation))
    }

    @Test("fails to resume a backup when no state is found")
    func failsToResumeMissingBackup() async throws {
        let operation = Operations.generateId()
        let executor = makeExecutor()
        let result = Box<Error?>(nil)

        _ = await executor.resumeBackup(operation: operation) { result.set($0) }

        await waitUntil { result.value != nil }
        #expect(result.value as? OperationExecutorError
            == .cannotResumeMissing(operation: operation))
    }

    private func makeExecutor(
        api: MockServerApiEndpointClient? = nil,
        core: MockServerCoreEndpointClient? = nil,
        backupTracker: any BackupTracker = MockBackupTracker(),
        recoveryTracker: any RecoveryTracker = MockRecoveryTracker(),
        restrictions: [OperationRestriction] = []
    ) -> DefaultOperationExecutor {
        let actualApi = api ?? MockServerApiEndpointClient(selfDevice: Fixtures.Datasets.default.device)
        let actualCore = core ?? MockServerCoreEndpointClient()
        let clients = StaticClients(api: actualApi, core: actualCore)

        let backupProviders = BackupProviders(
            checksum: Checksums.md5,
            staging: MockFileStaging(),
            compression: MockCompression(),
            encryptor: MockEncrypting(),
            decryptor: MockDecrypting(),
            clients: clients,
            track: backupTracker,
            analytics: NoOpAnalyticsCollector()
        )

        let recoveryProviders = RecoveryProviders(
            checksum: Checksums.md5,
            staging: MockFileStaging(),
            compression: MockCompression(),
            decryptor: MockDecrypting(),
            clients: clients,
            track: recoveryTracker,
            analytics: NoOpAnalyticsCollector()
        )

        return DefaultOperationExecutor(
            config: .init(backup: .init(limits: .init(maxPartSize: 16_384))),
            deviceSecret: { Fixtures.Secrets.default },
            backupProviders: backupProviders,
            recoveryProviders: recoveryProviders,
            restrictions: { _ in restrictions }
        )
    }
}

private final class StatefulBackupTracker: BackupTracker {
    private let tracker: MockBackupTracker
    private let providedState: BackupState

    init(state: BackupState, tracker: MockBackupTracker = MockBackupTracker()) {
        self.providedState = state
        self.tracker = tracker
    }

    var statistics: [MockBackupTracker.Statistic: Int] { tracker.statistics }

    func started(operation: OperationId, definition: DatasetDefinitionId) async {
        await tracker.started(operation: operation, definition: definition)
    }
    func entityDiscovered(operation: OperationId, entity: URL) async {
        await tracker.entityDiscovered(operation: operation, entity: entity)
    }
    func specificationProcessed(operation: OperationId, unmatched: [(Rule, any Error)]) async {
        await tracker.specificationProcessed(operation: operation, unmatched: unmatched)
    }
    func entityExamined(operation: OperationId, entity: URL) async {
        await tracker.entityExamined(operation: operation, entity: entity)
    }
    func entitySkipped(operation: OperationId, entity: URL) async {
        await tracker.entitySkipped(operation: operation, entity: entity)
    }
    func entityCollected(operation: OperationId, entity: SourceEntity) async {
        await tracker.entityCollected(operation: operation, entity: entity)
    }
    func entityProcessingStarted(operation: OperationId, entity: URL, expectedParts: Int) async {
        await tracker.entityProcessingStarted(operation: operation, entity: entity, expectedParts: expectedParts)
    }
    func entityPartProcessed(operation: OperationId, entity: URL) async {
        await tracker.entityPartProcessed(operation: operation, entity: entity)
    }
    func entityProcessed(operation: OperationId, entity: URL, metadata: Either<EntityMetadata, EntityMetadata>) async {
        await tracker.entityProcessed(operation: operation, entity: entity, metadata: metadata)
    }
    func metadataCollected(operation: OperationId) async {
        await tracker.metadataCollected(operation: operation)
    }
    func metadataPushed(operation: OperationId, entry: DatasetEntryId) async {
        await tracker.metadataPushed(operation: operation, entry: entry)
    }
    func failureEncountered(operation: OperationId, failure: any Error) async {
        await tracker.failureEncountered(operation: operation, failure: failure)
    }
    func failureEncountered(operation: OperationId, entity: URL, failure: any Error) async {
        await tracker.failureEncountered(operation: operation, entity: entity, failure: failure)
    }
    func completed(operation: OperationId) async {
        await tracker.completed(operation: operation)
    }
    func stateOf(operation: OperationId) async -> BackupState? { providedState }
}
