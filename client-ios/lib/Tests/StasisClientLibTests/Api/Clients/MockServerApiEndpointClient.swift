import Foundation
@testable import StasisClientLib

actor MockServerApiEndpointClient: ServerApiEndpointClient {
    nonisolated let selfDevice: DeviceId
    nonisolated let server: String = "mock-api-server"

    struct CallCounts: Sendable {
        var definitionsRetrieved = 0
        var definitionRetrieved = 0
        var definitionCreated = 0
        var definitionUpdated = 0
        var definitionDeleted = 0
        var entriesRetrieved = 0
        var entryRetrieved = 0
        var entryLatestRetrieved = 0
        var entryCreated = 0
        var entryDeleted = 0
        var publicSchedulesRetrieved = 0
        var publicScheduleRetrieved = 0
        var metadataWithIdRetrieved = 0
        var metadataWithEntryRetrieved = 0
        var userRetrieved = 0
        var userSaltReset = 0
        var userPasswordUpdated = 0
        var deviceRetrieved = 0
        var deviceKeyPushed = 0
        var deviceKeyPulled = 0
        var deviceKeyExistsChecked = 0
        var pinged = 0
        var commandsRetrieved = 0
        var analyticsEntriesSent = 0
    }

    private(set) var calls = CallCounts()
    private var lastRequest: (any Sendable)?

    init(selfDevice: DeviceId = UUID()) {
        self.selfDevice = selfDevice
    }

    func datasetDefinitions() async throws -> [DatasetDefinition] {
        calls.definitionsRetrieved += 1
        return [TestGenerators.definition(), TestGenerators.definition()]
    }

    func datasetDefinition(definition: DatasetDefinitionId) async throws -> DatasetDefinition {
        calls.definitionRetrieved += 1
        return TestGenerators.definition(id: definition)
    }

    func createDatasetDefinition(request: CreateDatasetDefinition) async throws -> CreatedDatasetDefinition {
        calls.definitionCreated += 1
        lastRequest = request
        return CreatedDatasetDefinition(definition: UUID())
    }

    func updateDatasetDefinition(definition: DatasetDefinitionId, request: UpdateDatasetDefinition) async throws {
        calls.definitionUpdated += 1
        lastRequest = request
    }

    func deleteDatasetDefinition(definition: DatasetDefinitionId) async throws {
        calls.definitionDeleted += 1
    }

    func datasetEntries(definition: DatasetDefinitionId) async throws -> [DatasetEntry] {
        calls.entriesRetrieved += 1
        return [
            TestGenerators.entry(definition: definition),
            TestGenerators.entry(definition: definition),
            TestGenerators.entry(definition: definition)
        ]
    }

    func datasetEntry(entry: DatasetEntryId) async throws -> DatasetEntry {
        calls.entryRetrieved += 1
        return TestGenerators.entry(id: entry)
    }

    func latestEntry(definition: DatasetDefinitionId, until: Date?) async throws -> DatasetEntry? {
        calls.entryLatestRetrieved += 1
        return TestGenerators.entry(definition: definition)
    }

    func createDatasetEntry(request: CreateDatasetEntry) async throws -> CreatedDatasetEntry {
        calls.entryCreated += 1
        lastRequest = request
        return CreatedDatasetEntry(entry: UUID())
    }

    func deleteDatasetEntry(entry: DatasetEntryId) async throws {
        calls.entryDeleted += 1
    }

    func publicSchedules() async throws -> [Schedule] {
        calls.publicSchedulesRetrieved += 1
        return [
            TestGenerators.schedule(isPublic: true),
            TestGenerators.schedule(isPublic: true),
            TestGenerators.schedule(isPublic: true)
        ]
    }

    func publicSchedule(schedule: ScheduleId) async throws -> Schedule {
        calls.publicScheduleRetrieved += 1
        return TestGenerators.schedule(id: schedule, isPublic: true)
    }

    func datasetMetadata(entry: DatasetEntryId) async throws -> DatasetMetadata {
        calls.metadataWithIdRetrieved += 1
        return TestGenerators.emptyDatasetMetadata
    }

    func datasetMetadata(entry: DatasetEntry) async throws -> DatasetMetadata {
        calls.metadataWithEntryRetrieved += 1
        return TestGenerators.emptyDatasetMetadata
    }

    func user() async throws -> User {
        calls.userRetrieved += 1
        return TestGenerators.user()
    }

    func resetUserSalt() async throws -> UpdatedUserSalt {
        calls.userSaltReset += 1
        return UpdatedUserSalt(salt: "test-salt")
    }

    func resetUserPassword(request: ResetUserPassword) async throws {
        calls.userPasswordUpdated += 1
        lastRequest = request
    }

    func device() async throws -> Device {
        calls.deviceRetrieved += 1
        return TestGenerators.device()
    }

    func pushDeviceKey(key: Data) async throws {
        calls.deviceKeyPushed += 1
    }

    func pullDeviceKey() async throws -> Data {
        calls.deviceKeyPulled += 1
        return Data("test-key".utf8)
    }

    func deviceKeyExists() async throws -> Bool {
        calls.deviceKeyExistsChecked += 1
        return false
    }

    func ping() async throws -> Ping {
        calls.pinged += 1
        return Ping(id: UUID())
    }

    func commands(lastSequenceId: Int64?) async throws -> [CommandAsJson] {
        calls.commandsRetrieved += 1
        let logoutNone = CommandAsJson.CommandParametersAsJson(logoutUser: nil)
        let logoutWithReason = CommandAsJson.CommandParametersAsJson(logoutUser: .init(reason: "test"))
        let all = [
            CommandAsJson(sequenceId: 1, source: "user", target: nil, parameters: logoutNone, created: Date()),
            CommandAsJson(
                sequenceId: 2,
                source: "service",
                target: selfDevice,
                parameters: logoutWithReason,
                created: Date()
            ),
            CommandAsJson(sequenceId: 3, source: "user", target: nil, parameters: logoutNone, created: Date())
        ]
        return all.filter { $0.sequenceId > (lastSequenceId ?? 0) }
    }

    func sendAnalyticsEntry(_ entry: AnalyticsEntry) async throws {
        calls.analyticsEntriesSent += 1
    }
}
