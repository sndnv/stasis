import Foundation

public protocol ServerApiEndpointClient: ServiceApiClient, AnalyticsClient {
    var selfDevice: DeviceId { get }
    var server: String { get }

    func datasetDefinitions() async throws -> [DatasetDefinition]
    func datasetDefinition(definition: DatasetDefinitionId) async throws -> DatasetDefinition
    func createDatasetDefinition(request: CreateDatasetDefinition) async throws -> CreatedDatasetDefinition
    func updateDatasetDefinition(definition: DatasetDefinitionId, request: UpdateDatasetDefinition) async throws
    func deleteDatasetDefinition(definition: DatasetDefinitionId) async throws

    func datasetEntries(definition: DatasetDefinitionId) async throws -> [DatasetEntry]
    func datasetEntry(entry: DatasetEntryId) async throws -> DatasetEntry
    func latestEntry(definition: DatasetDefinitionId, until: Date?) async throws -> DatasetEntry?
    func createDatasetEntry(request: CreateDatasetEntry) async throws -> CreatedDatasetEntry
    func deleteDatasetEntry(entry: DatasetEntryId) async throws

    func publicSchedules() async throws -> [Schedule]
    func publicSchedule(schedule: ScheduleId) async throws -> Schedule

    func datasetMetadata(entry: DatasetEntryId) async throws -> DatasetMetadata
    func datasetMetadata(entry: DatasetEntry) async throws -> DatasetMetadata

    func user() async throws -> User
    func resetUserSalt() async throws -> UpdatedUserSalt
    func resetUserPassword(request: ResetUserPassword) async throws

    func device() async throws -> Device
    func pushDeviceKey(key: Data) async throws
    func pullDeviceKey() async throws -> Data
    func deviceKeyExists() async throws -> Bool

    func ping() async throws -> Ping
    func commands(lastSequenceId: Int64?) async throws -> [CommandAsJson]
}

public enum DecryptionContext: Sendable {
    case disabled
}
