import Foundation

public actor DefaultCacheRefreshHandler: CacheRefreshHandler {
    private let underlying: any ServerApiEndpointClient
    private let datasetDefinitionsCache: any Cache<DatasetDefinitionId, DatasetDefinition>
    private let datasetEntriesCache: any Cache<DatasetEntryId, DatasetEntry>
    private let datasetEntriesForDefinitionCache: any Cache<DatasetDefinitionId, DatasetEntriesForDefinition>

    public init(
        underlying: any ServerApiEndpointClient,
        datasetDefinitionsCache: any Cache<DatasetDefinitionId, DatasetDefinition>,
        datasetEntriesCache: any Cache<DatasetEntryId, DatasetEntry>,
        datasetEntriesForDefinitionCache: any Cache<DatasetDefinitionId, DatasetEntriesForDefinition>
    ) {
        self.underlying = underlying
        self.datasetDefinitionsCache = datasetDefinitionsCache
        self.datasetEntriesCache = datasetEntriesCache
        self.datasetEntriesForDefinitionCache = datasetEntriesForDefinitionCache
    }

    public func refreshNow(target: CacheRefreshTarget) async throws {
        switch target {
        case .allDatasetDefinitions:
            let definitions = try await underlying.datasetDefinitions()
            try await datasetDefinitionsCache.clear()
            try await datasetDefinitionsCache.put(
                entries: Dictionary(uniqueKeysWithValues: definitions.map { ($0.id, $0) })
            )
        case .allDatasetEntries(let definition):
            let entries = try await underlying.datasetEntries(definition: definition)
            try await datasetEntriesCache.put(
                entries: Dictionary(uniqueKeysWithValues: entries.map { ($0.id, $0) })
            )
            try await datasetEntriesForDefinitionCache.put(
                definition, DatasetEntriesForDefinition(entries: entries)
            )
        case .latestDatasetEntry(let definition):
            if let entry = try await underlying.latestEntry(definition: definition, until: nil) {
                try await datasetEntriesCache.put(entry.id, entry)
                let current = await datasetEntriesForDefinitionCache.get(definition) ?? .empty()
                try await datasetEntriesForDefinitionCache.put(definition, current.with(entry: entry))
            } else {
                try await datasetEntriesForDefinitionCache.remove(definition)
            }
        case .individualDatasetDefinition(let definition):
            let resolved = try await underlying.datasetDefinition(definition: definition)
            try await datasetDefinitionsCache.put(resolved.id, resolved)
        case .individualDatasetEntry(let entry):
            let resolved = try await underlying.datasetEntry(entry: entry)
            try await datasetEntriesCache.put(resolved.id, resolved)
            let current = await datasetEntriesForDefinitionCache.get(resolved.definition) ?? .empty()
            try await datasetEntriesForDefinitionCache.put(resolved.definition, current.with(entry: resolved))
        }
    }
}
