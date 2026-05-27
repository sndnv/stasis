import Foundation

public struct CachedServerApiEndpointClient: ServerApiEndpointClient {
    public let underlying: any ServerApiEndpointClient
    public let datasetDefinitionsCache: any Cache<DatasetDefinitionId, DatasetDefinition>
    public let datasetEntriesCache: any Cache<DatasetEntryId, DatasetEntry>
    public let datasetEntriesForDefinitionCache: any Cache<DatasetDefinitionId, DatasetEntriesForDefinition>
    public let datasetMetadataCache: any Cache<DatasetEntryId, DatasetMetadata>
    public let refreshHandler: any CacheRefreshHandler

    public init(
        underlying: any ServerApiEndpointClient,
        datasetDefinitionsCache: any Cache<DatasetDefinitionId, DatasetDefinition>,
        datasetEntriesCache: any Cache<DatasetEntryId, DatasetEntry>,
        datasetEntriesForDefinitionCache: any Cache<DatasetDefinitionId, DatasetEntriesForDefinition>,
        datasetMetadataCache: any Cache<DatasetEntryId, DatasetMetadata>,
        refreshHandler: any CacheRefreshHandler
    ) {
        self.underlying = underlying
        self.datasetDefinitionsCache = datasetDefinitionsCache
        self.datasetEntriesCache = datasetEntriesCache
        self.datasetEntriesForDefinitionCache = datasetEntriesForDefinitionCache
        self.datasetMetadataCache = datasetMetadataCache
        self.refreshHandler = refreshHandler
    }

    public var selfDevice: DeviceId { underlying.selfDevice }
    public var server: String { underlying.server }

    public func datasetDefinitions() async throws -> [DatasetDefinition] {
        var cached = await datasetDefinitionsCache.all()
        if cached.isEmpty {
            try await refreshHandler.refreshNow(target: .allDatasetDefinitions)
            cached = await datasetDefinitionsCache.all()
        }
        return cached.values.sorted { $0.info < $1.info }
    }

    public func datasetDefinition(definition: DatasetDefinitionId) async throws -> DatasetDefinition {
        if let cached = await datasetDefinitionsCache.get(definition) {
            return cached
        }
        try await refreshHandler.refreshNow(target: .individualDatasetDefinition(definition: definition))
        guard let refreshed = await datasetDefinitionsCache.get(definition) else {
            throw ResourceMissingFailure()
        }
        return refreshed
    }

    public func createDatasetDefinition(request: CreateDatasetDefinition) async throws -> CreatedDatasetDefinition {
        let created = try await underlying.createDatasetDefinition(request: request)
        try await refreshHandler.refreshNow(target: .individualDatasetDefinition(definition: created.definition))
        return created
    }

    public func updateDatasetDefinition(definition: DatasetDefinitionId, request: UpdateDatasetDefinition) async throws {
        try await underlying.updateDatasetDefinition(definition: definition, request: request)
        try await refreshHandler.refreshNow(target: .individualDatasetDefinition(definition: definition))
    }

    public func deleteDatasetDefinition(definition: DatasetDefinitionId) async throws {
        try await underlying.deleteDatasetDefinition(definition: definition)
        try? await datasetDefinitionsCache.remove(definition)
        try? await datasetEntriesForDefinitionCache.remove(definition)
    }

    public func datasetEntries(definition: DatasetDefinitionId) async throws -> [DatasetEntry] {
        var summary = await datasetEntriesForDefinitionCache.get(definition)
        if summary == nil {
            try await refreshHandler.refreshNow(target: .allDatasetEntries(definition: definition))
            summary = await datasetEntriesForDefinitionCache.get(definition)
        }
        guard let summary else { return [] }
        var entries: [DatasetEntry] = []
        for id in summary.entries.keys {
            if let cached = await datasetEntriesCache.get(id) {
                entries.append(cached)
            }
        }
        return entries
    }

    public func datasetEntry(entry: DatasetEntryId) async throws -> DatasetEntry {
        if let cached = await datasetEntriesCache.get(entry) {
            return cached
        }
        try await refreshHandler.refreshNow(target: .individualDatasetEntry(entry: entry))
        guard let refreshed = await datasetEntriesCache.get(entry) else {
            throw ResourceMissingFailure()
        }
        return refreshed
    }

    public func latestEntry(definition: DatasetDefinitionId, until: Date?) async throws -> DatasetEntry? {
        if let until {
            return try await underlying.latestEntry(definition: definition, until: until)
        }
        let entries = try await datasetEntries(definition: definition)
        return entries.max(by: { $0.created < $1.created })
    }

    public func createDatasetEntry(request: CreateDatasetEntry) async throws -> CreatedDatasetEntry {
        let created = try await underlying.createDatasetEntry(request: request)
        try await refreshHandler.refreshNow(target: .individualDatasetEntry(entry: created.entry))
        return created
    }

    public func deleteDatasetEntry(entry: DatasetEntryId) async throws {
        try await underlying.deleteDatasetEntry(entry: entry)
        if let cached = await datasetEntriesCache.get(entry) {
            try? await datasetEntriesCache.remove(entry)
            try await refreshHandler.refreshNow(target: .allDatasetEntries(definition: cached.definition))
        }
    }

    public func publicSchedules() async throws -> [Schedule] {
        try await underlying.publicSchedules()
    }

    public func publicSchedule(schedule: ScheduleId) async throws -> Schedule {
        try await underlying.publicSchedule(schedule: schedule)
    }

    public func datasetMetadata(entry: DatasetEntryId) async throws -> DatasetMetadata {
        let loaded = try await datasetMetadataCache.getOrLoad(entry) { id in
            try await self.underlying.datasetMetadata(entry: id)
        }
        guard let loaded else { throw ResourceMissingFailure() }
        return loaded
    }

    public func datasetMetadata(entry: DatasetEntry) async throws -> DatasetMetadata {
        let loaded = try await datasetMetadataCache.getOrLoad(entry.id) { _ in
            try await self.underlying.datasetMetadata(entry: entry)
        }
        guard let loaded else { throw ResourceMissingFailure() }
        return loaded
    }

    public func user() async throws -> User { try await underlying.user() }
    public func resetUserSalt() async throws -> UpdatedUserSalt { try await underlying.resetUserSalt() }
    public func resetUserPassword(request: ResetUserPassword) async throws { try await underlying.resetUserPassword(request: request) }

    public func device() async throws -> Device { try await underlying.device() }
    public func pushDeviceKey(key: Data) async throws { try await underlying.pushDeviceKey(key: key) }
    public func pullDeviceKey() async throws -> Data { try await underlying.pullDeviceKey() }
    public func deviceKeyExists() async throws -> Bool { try await underlying.deviceKeyExists() }

    public func ping() async throws -> Ping { try await underlying.ping() }
    public func commands(lastSequenceId: Int64?) async throws -> [CommandAsJson] {
        try await underlying.commands(lastSequenceId: lastSequenceId)
    }

    public func sendAnalyticsEntry(_ entry: AnalyticsEntry) async throws {
        try await underlying.sendAnalyticsEntry(entry)
    }
}
