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
    private(set) var lastRequest: (any Sendable)?
    private var datasetMetadataOverrides: [DatasetEntryId: DatasetMetadata] = [:]
    private var datasetDefinitionsOverride: [DatasetDefinition]?
    private var latestEntryOverrides: [DatasetDefinitionId: DatasetEntryId?] = [:]
    private let pingDisabled: Bool
    private let commandsDisabled: Bool
    private let createDatasetEntryDelay: TimeInterval
    private var datasetDefinitionFailure: (any Error)?
    private var latestEntryFailure: (any Error)?
    private var datasetEntryFailure: (any Error)?

    func lastRequest<T: Sendable>(as type: T.Type = T.self) -> T? {
        lastRequest as? T
    }

    init(
        selfDevice: DeviceId = UUID(),
        pingDisabled: Bool = false,
        commandsDisabled: Bool = false,
        createDatasetEntryDelay: TimeInterval = 0
    ) {
        self.selfDevice = selfDevice
        self.pingDisabled = pingDisabled
        self.commandsDisabled = commandsDisabled
        self.createDatasetEntryDelay = createDatasetEntryDelay
    }

    func setDatasetDefinitionFailure(_ error: any Error) {
        datasetDefinitionFailure = error
    }

    func setLatestEntryFailure(_ error: any Error) {
        latestEntryFailure = error
    }

    func setDatasetEntryFailure(_ error: any Error) {
        datasetEntryFailure = error
    }

    func setDatasetMetadataOverride(_ entry: DatasetEntryId, _ metadata: DatasetMetadata) {
        datasetMetadataOverrides[entry] = metadata
    }

    func setDatasetDefinitionsOverride(_ definitions: [DatasetDefinition]) {
        datasetDefinitionsOverride = definitions
    }

    func setLatestEntryOverride(_ definition: DatasetDefinitionId, _ entry: DatasetEntryId?) {
        latestEntryOverrides[definition] = entry
    }

    func datasetDefinitions() async throws -> [DatasetDefinition] {
        calls.definitionsRetrieved += 1
        if let override = datasetDefinitionsOverride { return override }
        return [TestGenerators.definition(), TestGenerators.definition()]
    }

    func datasetDefinition(definition: DatasetDefinitionId) async throws -> DatasetDefinition {
        calls.definitionRetrieved += 1
        if let datasetDefinitionFailure {
            throw datasetDefinitionFailure
        }
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
        if let datasetEntryFailure {
            throw datasetEntryFailure
        }
        return TestGenerators.entry(id: entry)
    }

    func latestEntry(definition: DatasetDefinitionId, until: Date?) async throws -> DatasetEntry? {
        calls.entryLatestRetrieved += 1
        if let latestEntryFailure {
            throw latestEntryFailure
        }
        if let override = latestEntryOverrides[definition] {
            return override.map { TestGenerators.entry(id: $0, definition: definition) }
        }
        return TestGenerators.entry(definition: definition)
    }

    func createDatasetEntry(request: CreateDatasetEntry) async throws -> CreatedDatasetEntry {
        calls.entryCreated += 1
        lastRequest = request
        if createDatasetEntryDelay > 0 {
            try await Task.sleep(nanoseconds: UInt64(createDatasetEntryDelay * 1_000_000_000))
        }
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
        return datasetMetadataOverrides[entry] ?? TestGenerators.emptyDatasetMetadata
    }

    func datasetMetadata(entry: DatasetEntry) async throws -> DatasetMetadata {
        calls.metadataWithEntryRetrieved += 1
        return datasetMetadataOverrides[entry.id] ?? TestGenerators.emptyDatasetMetadata
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
        if pingDisabled {
            throw EndpointFailure(message: "[pingDisabled] is set to [true]")
        }
        return Ping(id: UUID())
    }

    func commands(lastSequenceId: Int64?) async throws -> [CommandAsJson] {
        calls.commandsRetrieved += 1
        if commandsDisabled {
            throw EndpointFailure(message: "[commandsDisabled] is set to [true]")
        }
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
