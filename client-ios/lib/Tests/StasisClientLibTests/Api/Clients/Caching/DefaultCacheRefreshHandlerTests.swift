import Foundation
@testable import StasisClientLib
import StasisClientLibTestSupport
import Testing

@Suite("DefaultCacheRefreshHandler")
struct DefaultCacheRefreshHandlerTests {
    @Test("allDatasetDefinitions clears and repopulates the definitions cache")
    func allDatasetDefinitions() async throws {
        let api = MockServerApiEndpointClient()
        let definitionA = TestGenerators.definition(info: "alpha")
        let definitionB = TestGenerators.definition(info: "beta")
        await api.setDatasetDefinitionsOverride([definitionA, definitionB])
        let bundle = makeBundle(api: api)
        await bundle.definitionsCache.put(UUID(), TestGenerators.definition(info: "stale"))

        try await bundle.handler.refreshNow(target: .allDatasetDefinitions)

        let stored = await bundle.definitionsCache.all()
        #expect(stored.count == 2)
        #expect(stored[definitionA.id]?.info == "alpha")
        #expect(stored[definitionB.id]?.info == "beta")
    }

    @Test("allDatasetEntries populates both entries and entries-for-definition caches")
    func allDatasetEntries() async throws {
        let api = MockServerApiEndpointClient()
        let definition = UUID()
        let entryOne = TestGenerators.entry(definition: definition)
        let entryTwo = TestGenerators.entry(definition: definition)
        await api.setDatasetEntriesOverride([entryOne, entryTwo])
        let bundle = makeBundle(api: api)

        try await bundle.handler.refreshNow(target: .allDatasetEntries(definition: definition))

        let entries = await bundle.entriesCache.all()
        #expect(entries.keys.contains(entryOne.id))
        #expect(entries.keys.contains(entryTwo.id))
        let summary = await bundle.entriesForDefCache.get(definition)
        #expect(summary?.entries.count == 2)
    }

    @Test("latestDatasetEntry with entry populates and updates the summary")
    func latestEntryWithEntry() async throws {
        let api = MockServerApiEndpointClient()
        let definition = UUID()
        let latestId = UUID()
        await api.setLatestEntryOverride(definition, latestId)
        let bundle = makeBundle(api: api)

        try await bundle.handler.refreshNow(target: .latestDatasetEntry(definition: definition))

        let cached = await bundle.entriesCache.get(latestId)
        #expect(cached?.id == latestId)
        let summary = await bundle.entriesForDefCache.get(definition)
        #expect(summary?.latest == latestId)
    }

    @Test("latestDatasetEntry without entry removes the summary entry")
    func latestEntryWithoutEntry() async throws {
        let api = MockServerApiEndpointClient()
        let definition = UUID()
        await api.setLatestEntryOverride(definition, nil)
        let bundle = makeBundle(api: api)
        await bundle.entriesForDefCache.put(definition, .empty())

        try await bundle.handler.refreshNow(target: .latestDatasetEntry(definition: definition))

        let summary = await bundle.entriesForDefCache.get(definition)
        #expect(summary == nil)
    }

    @Test("individualDatasetDefinition populates a single definition entry")
    func individualDatasetDefinition() async throws {
        let api = MockServerApiEndpointClient()
        let definition = UUID()
        let bundle = makeBundle(api: api)

        try await bundle.handler.refreshNow(
            target: .individualDatasetDefinition(definition: definition)
        )

        let cached = await bundle.definitionsCache.get(definition)
        #expect(cached?.id == definition)
    }

    @Test("individualDatasetEntry populates entry and updates the entries-for-definition summary")
    func individualDatasetEntry() async throws {
        let api = MockServerApiEndpointClient()
        let entryId = UUID()
        let bundle = makeBundle(api: api)

        try await bundle.handler.refreshNow(target: .individualDatasetEntry(entry: entryId))

        let cached = await bundle.entriesCache.get(entryId)
        #expect(cached?.id == entryId)
        let summary = await bundle.entriesForDefCache.get(cached!.definition)
        #expect(summary?.entries[entryId] != nil)
    }

    private struct Bundle {
        let handler: DefaultCacheRefreshHandler
        let definitionsCache: MapCache<DatasetDefinitionId, DatasetDefinition>
        let entriesCache: MapCache<DatasetEntryId, DatasetEntry>
        let entriesForDefCache: MapCache<DatasetDefinitionId, DatasetEntriesForDefinition>
    }

    private func makeBundle(api: MockServerApiEndpointClient) -> Bundle {
        let definitionsCache = MapCache<DatasetDefinitionId, DatasetDefinition>()
        let entriesCache = MapCache<DatasetEntryId, DatasetEntry>()
        let entriesForDefCache = MapCache<DatasetDefinitionId, DatasetEntriesForDefinition>()
        let handler = DefaultCacheRefreshHandler(
            underlying: api,
            datasetDefinitionsCache: definitionsCache,
            datasetEntriesCache: entriesCache,
            datasetEntriesForDefinitionCache: entriesForDefCache
        )
        return Bundle(
            handler: handler,
            definitionsCache: definitionsCache,
            entriesCache: entriesCache,
            entriesForDefCache: entriesForDefCache
        )
    }
}
