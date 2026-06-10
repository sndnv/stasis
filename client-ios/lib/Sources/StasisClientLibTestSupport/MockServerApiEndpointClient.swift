import Foundation
import StasisClientLib

public actor MockServerApiEndpointClient: ServerApiEndpointClient {
    nonisolated public let selfDevice: DeviceId
    nonisolated public let server: String = "mock-api-server"

    public struct CallCounts: Sendable {
        public var definitionsRetrieved = 0
        public var definitionRetrieved = 0
        public var definitionCreated = 0
        public var definitionUpdated = 0
        public var definitionDeleted = 0
        public var entriesRetrieved = 0
        public var entryRetrieved = 0
        public var entryLatestRetrieved = 0
        public var entryCreated = 0
        public var entryDeleted = 0
        public var publicSchedulesRetrieved = 0
        public var publicScheduleRetrieved = 0
        public var metadataWithIdRetrieved = 0
        public var metadataWithEntryRetrieved = 0
        public var userRetrieved = 0
        public var userSaltReset = 0
        public var userPasswordUpdated = 0
        public var deviceRetrieved = 0
        public var deviceKeyPushed = 0
        public var deviceKeyPulled = 0
        public var deviceKeyExistsChecked = 0
        public var pinged = 0
        public var commandsRetrieved = 0
        public var analyticsEntriesSent = 0
    }

    public private(set) var calls = CallCounts()
    public private(set) var lastRequest: (any Sendable)?
    private var datasetMetadataOverrides: [DatasetEntryId: DatasetMetadata] = [:]
    private var datasetDefinitionsOverride: [DatasetDefinition]?
    private var latestEntryOverrides: [DatasetDefinitionId: DatasetEntryId?] = [:]
    private var deviceKeyExistsOverride: Bool?
    private var pullDeviceKeyOverride: Data?
    private let pingDisabled: Bool
    private let commandsDisabled: Bool
    private let createDatasetEntryDelay: TimeInterval
    private var datasetDefinitionsFailure: (any Error)?
    private var datasetDefinitionFailure: (any Error)?
    private var latestEntryFailure: (any Error)?
    private var datasetEntryFailure: (any Error)?
    private var datasetEntriesFailure: (any Error)?
    private var datasetMetadataFailure: (any Error)?
    private var datasetEntriesOverride: [DatasetEntry]?
    private var pushDeviceKeyFailure: (any Error)?
    private var pullDeviceKeyFailure: (any Error)?
    private var deviceKeyExistsFailure: (any Error)?
    private var userFailure: (any Error)?
    private var deviceFailure: (any Error)?

    public func lastRequest<T: Sendable>(as type: T.Type = T.self) -> T? {
        lastRequest as? T
    }

    public init(
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

    public func setDatasetDefinitionsFailure(_ error: any Error) { datasetDefinitionsFailure = error }
    public func setDatasetDefinitionFailure(_ error: any Error) { datasetDefinitionFailure = error }
    public func setLatestEntryFailure(_ error: any Error) { latestEntryFailure = error }
    public func setDatasetEntryFailure(_ error: any Error) { datasetEntryFailure = error }
    public func setDatasetEntriesFailure(_ error: any Error) { datasetEntriesFailure = error }
    public func setDatasetMetadataFailure(_ error: any Error) { datasetMetadataFailure = error }
    public func setDatasetEntriesOverride(_ entries: [DatasetEntry]) { datasetEntriesOverride = entries }
    public func setPushDeviceKeyFailure(_ error: any Error) { pushDeviceKeyFailure = error }
    public func setPullDeviceKeyFailure(_ error: any Error) { pullDeviceKeyFailure = error }
    public func setDeviceKeyExistsFailure(_ error: any Error) { deviceKeyExistsFailure = error }
    public func setUserFailure(_ error: any Error) { userFailure = error }
    public func setDeviceFailure(_ error: any Error) { deviceFailure = error }

    public func setDatasetMetadataOverride(_ entry: DatasetEntryId, _ metadata: DatasetMetadata) {
        datasetMetadataOverrides[entry] = metadata
    }

    public func setDatasetDefinitionsOverride(_ definitions: [DatasetDefinition]) {
        datasetDefinitionsOverride = definitions
    }

    public func setLatestEntryOverride(_ definition: DatasetDefinitionId, _ entry: DatasetEntryId?) {
        latestEntryOverrides[definition] = entry
    }

    public func setDeviceKeyExistsOverride(_ value: Bool) { deviceKeyExistsOverride = value }
    public func setPullDeviceKeyOverride(_ value: Data) { pullDeviceKeyOverride = value }

    public func datasetDefinitions() async throws -> [DatasetDefinition] {
        calls.definitionsRetrieved += 1
        if let datasetDefinitionsFailure { throw datasetDefinitionsFailure }
        if let override = datasetDefinitionsOverride { return override }
        return [TestGenerators.definition(), TestGenerators.definition()]
    }

    public func datasetDefinition(definition: DatasetDefinitionId) async throws -> DatasetDefinition {
        calls.definitionRetrieved += 1
        if let datasetDefinitionFailure { throw datasetDefinitionFailure }
        return TestGenerators.definition(id: definition)
    }

    public func createDatasetDefinition(request: CreateDatasetDefinition) async throws -> CreatedDatasetDefinition {
        calls.definitionCreated += 1
        lastRequest = request
        return CreatedDatasetDefinition(definition: UUID())
    }

    public func updateDatasetDefinition(
        definition: DatasetDefinitionId,
        request: UpdateDatasetDefinition
    ) async throws {
        calls.definitionUpdated += 1
        lastRequest = request
    }

    public func deleteDatasetDefinition(definition: DatasetDefinitionId) async throws {
        calls.definitionDeleted += 1
    }

    public func datasetEntries(definition: DatasetDefinitionId) async throws -> [DatasetEntry] {
        calls.entriesRetrieved += 1
        if let datasetEntriesFailure { throw datasetEntriesFailure }
        if let datasetEntriesOverride { return datasetEntriesOverride }
        return [
            TestGenerators.entry(definition: definition),
            TestGenerators.entry(definition: definition),
            TestGenerators.entry(definition: definition)
        ]
    }

    public func datasetEntry(entry: DatasetEntryId) async throws -> DatasetEntry {
        calls.entryRetrieved += 1
        if let datasetEntryFailure { throw datasetEntryFailure }
        return TestGenerators.entry(id: entry)
    }

    public func latestEntry(definition: DatasetDefinitionId, until: Date?) async throws -> DatasetEntry? {
        calls.entryLatestRetrieved += 1
        if let latestEntryFailure { throw latestEntryFailure }
        if let override = latestEntryOverrides[definition] {
            return override.map { TestGenerators.entry(id: $0, definition: definition) }
        }
        return TestGenerators.entry(definition: definition)
    }

    public func createDatasetEntry(request: CreateDatasetEntry) async throws -> CreatedDatasetEntry {
        calls.entryCreated += 1
        lastRequest = request
        if createDatasetEntryDelay > 0 {
            try await Task.sleep(nanoseconds: UInt64(createDatasetEntryDelay * 1_000_000_000))
        }
        return CreatedDatasetEntry(entry: UUID())
    }

    public func deleteDatasetEntry(entry: DatasetEntryId) async throws {
        calls.entryDeleted += 1
    }

    public func publicSchedules() async throws -> [Schedule] {
        calls.publicSchedulesRetrieved += 1
        return [
            TestGenerators.schedule(isPublic: true),
            TestGenerators.schedule(isPublic: true),
            TestGenerators.schedule(isPublic: true)
        ]
    }

    public func publicSchedule(schedule: ScheduleId) async throws -> Schedule {
        calls.publicScheduleRetrieved += 1
        return TestGenerators.schedule(id: schedule, isPublic: true)
    }

    public func datasetMetadata(entry: DatasetEntryId) async throws -> DatasetMetadata {
        calls.metadataWithIdRetrieved += 1
        if let datasetMetadataFailure { throw datasetMetadataFailure }
        return datasetMetadataOverrides[entry] ?? TestGenerators.emptyDatasetMetadata
    }

    public func datasetMetadata(entry: DatasetEntry) async throws -> DatasetMetadata {
        calls.metadataWithEntryRetrieved += 1
        if let datasetMetadataFailure { throw datasetMetadataFailure }
        return datasetMetadataOverrides[entry.id] ?? TestGenerators.emptyDatasetMetadata
    }

    public func user() async throws -> User {
        calls.userRetrieved += 1
        if let userFailure { throw userFailure }
        return TestGenerators.user()
    }

    public func resetUserSalt() async throws -> UpdatedUserSalt {
        calls.userSaltReset += 1
        return UpdatedUserSalt(salt: "test-salt")
    }

    public func resetUserPassword(request: ResetUserPassword) async throws {
        calls.userPasswordUpdated += 1
        lastRequest = request
    }

    public func device() async throws -> Device {
        calls.deviceRetrieved += 1
        if let deviceFailure { throw deviceFailure }
        return TestGenerators.device()
    }

    public func pushDeviceKey(key: Data) async throws {
        calls.deviceKeyPushed += 1
        if let pushDeviceKeyFailure { throw pushDeviceKeyFailure }
    }

    public func pullDeviceKey() async throws -> Data {
        calls.deviceKeyPulled += 1
        if let pullDeviceKeyFailure { throw pullDeviceKeyFailure }
        return pullDeviceKeyOverride ?? Data("test-key".utf8)
    }

    public func deviceKeyExists() async throws -> Bool {
        calls.deviceKeyExistsChecked += 1
        if let deviceKeyExistsFailure { throw deviceKeyExistsFailure }
        return deviceKeyExistsOverride ?? false
    }

    public func ping() async throws -> Ping {
        calls.pinged += 1
        if pingDisabled {
            throw EndpointFailure(message: "[pingDisabled] is set to [true]")
        }
        return Ping(id: UUID())
    }

    public func commands(lastSequenceId: Int64?) async throws -> [CommandAsJson] {
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

    public func sendAnalyticsEntry(_ entry: AnalyticsEntry) async throws {
        calls.analyticsEntriesSent += 1
    }
}
