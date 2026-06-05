import Foundation
@testable import StasisClientLib
import StasisClientLibTestSupport

actor MockCacheRefreshHandler: CacheRefreshHandler {
    private let underlying: MockServerApiEndpointClient
    private let definitionsCache: any Cache<DatasetDefinitionId, DatasetDefinition>
    private let entriesCache: any Cache<DatasetEntryId, DatasetEntry>
    private let entriesForDefCache: any Cache<DatasetDefinitionId, DatasetEntriesForDefinition>

    private(set) var refreshTargets: [CacheRefreshTarget] = []

    init(
        underlying: MockServerApiEndpointClient,
        definitionsCache: any Cache<DatasetDefinitionId, DatasetDefinition>,
        entriesCache: any Cache<DatasetEntryId, DatasetEntry>,
        entriesForDefCache: any Cache<DatasetDefinitionId, DatasetEntriesForDefinition>
    ) {
        self.underlying = underlying
        self.definitionsCache = definitionsCache
        self.entriesCache = entriesCache
        self.entriesForDefCache = entriesForDefCache
    }

    func refreshNow(target: CacheRefreshTarget) async throws {
        refreshTargets.append(target)

        switch target {
        case .allDatasetDefinitions:
            let definitions = try await underlying.datasetDefinitions()
            try await definitionsCache.clear()
            try await definitionsCache.put(entries: Dictionary(uniqueKeysWithValues: definitions.map { ($0.id, $0) }))
        case .allDatasetEntries(let definition):
            let entries = try await underlying.datasetEntries(definition: definition)
            try await entriesCache.put(entries: Dictionary(uniqueKeysWithValues: entries.map { ($0.id, $0) }))
            try await entriesForDefCache.put(definition, DatasetEntriesForDefinition(entries: entries))
        case .latestDatasetEntry(let definition):
            if let entry = try await underlying.latestEntry(definition: definition, until: nil) {
                try await entriesCache.put(entry.id, entry)
                let current = await entriesForDefCache.get(definition) ?? .empty()
                try await entriesForDefCache.put(definition, current.with(entry: entry))
            } else {
                try await entriesForDefCache.remove(definition)
            }
        case .individualDatasetDefinition(let definition):
            if let resolved = try? await underlying.datasetDefinition(definition: definition) {
                try await definitionsCache.put(resolved.id, resolved)
            }
        case .individualDatasetEntry(let entry):
            if let resolved = try? await underlying.datasetEntry(entry: entry) {
                try await entriesCache.put(resolved.id, resolved)
                let current = await entriesForDefCache.get(resolved.definition) ?? .empty()
                try await entriesForDefCache.put(resolved.definition, current.with(entry: resolved))
            }
        }
    }

    func stop() {}
}
