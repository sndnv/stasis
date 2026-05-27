import Foundation
import fsi
@testable import StasisClientLib
import Testing

@Suite("CachedServerApiEndpointClient")
struct CachedServerApiEndpointClientTests {
    private func makeClient(
        underlying: MockServerApiEndpointClient = MockServerApiEndpointClient(),
        datasetDefinitionsCache: (any Cache<DatasetDefinitionId, DatasetDefinition>)? = nil,
        datasetEntriesCache: (any Cache<DatasetEntryId, DatasetEntry>)? = nil,
        datasetEntriesForDefinitionCache: (any Cache<DatasetDefinitionId, DatasetEntriesForDefinition>)? = nil,
        datasetMetadataCache: (any Cache<DatasetEntryId, DatasetMetadata>)? = nil
    ) -> CachedServerApiEndpointClient {
        let defsCache = datasetDefinitionsCache ?? MapCache<DatasetDefinitionId, DatasetDefinition>()
        let entriesCache = datasetEntriesCache ?? MapCache<DatasetEntryId, DatasetEntry>()
        let entriesForDefCache = datasetEntriesForDefinitionCache ?? MapCache<DatasetDefinitionId, DatasetEntriesForDefinition>()
        let metadataCache = datasetMetadataCache ?? MapCache<DatasetEntryId, DatasetMetadata>()
        let handler = MockCacheRefreshHandler(
            underlying: underlying,
            definitionsCache: defsCache,
            entriesCache: entriesCache,
            entriesForDefCache: entriesForDefCache
        )
        return CachedServerApiEndpointClient(
            underlying: underlying,
            datasetDefinitionsCache: defsCache,
            datasetEntriesCache: entriesCache,
            datasetEntriesForDefinitionCache: entriesForDefCache,
            datasetMetadataCache: metadataCache,
            refreshHandler: handler
        )
    }

    @Test("retrieves and caches all dataset definitions")
    func cachesAllDefinitions() async throws {
        let underlying = MockServerApiEndpointClient()
        let definitions = TrackingCache(underlying: MapCache<DatasetDefinitionId, DatasetDefinition>())
        let client = makeClient(underlying: underlying, datasetDefinitionsCache: definitions)

        for _ in 0..<8 { _ = try await client.datasetDefinitions() }

        #expect(await definitions.hits == 0)
        #expect(await definitions.misses == 0)
        #expect(await underlying.calls.definitionsRetrieved == 1)
    }

    @Test("retrieves and caches individual dataset definitions")
    func cachesIndividualDefinition() async throws {
        let underlying = MockServerApiEndpointClient()
        let definitions = TrackingCache(underlying: MapCache<DatasetDefinitionId, DatasetDefinition>())
        let client = makeClient(underlying: underlying, datasetDefinitionsCache: definitions)

        let definition1 = UUID()
        let definition2 = UUID()

        _ = try await client.datasetDefinition(definition: definition1)
        #expect(await definitions.hits == 1)
        #expect(await definitions.misses == 1)
        #expect(await underlying.calls.definitionRetrieved == 1)

        for _ in 0..<3 { _ = try await client.datasetDefinition(definition: definition1) }
        #expect(await definitions.hits == 4)
        #expect(await definitions.misses == 1)
        #expect(await underlying.calls.definitionRetrieved == 1)

        for _ in 0..<3 { _ = try await client.datasetDefinition(definition: definition2) }
        #expect(await definitions.hits == 7)
        #expect(await definitions.misses == 2)
        #expect(await underlying.calls.definitionRetrieved == 2)
    }

    @Test("creates dataset definitions and invalidates existing cache")
    func createInvalidatesCache() async throws {
        let underlying = MockServerApiEndpointClient(selfDevice: UUID())
        let definitions = TrackingCache(underlying: MapCache<DatasetDefinitionId, DatasetDefinition>())
        let client = makeClient(underlying: underlying, datasetDefinitionsCache: definitions)

        for _ in 0..<3 { _ = try await client.datasetDefinitions() }
        #expect(await definitions.hits == 0)
        #expect(await definitions.misses == 0)

        let definition = UUID()
        for _ in 0..<3 { _ = try await client.datasetDefinition(definition: definition) }
        #expect(await definitions.hits == 3)
        #expect(await definitions.misses == 1)
        #expect(await underlying.calls.definitionRetrieved == 1)
        #expect(await underlying.calls.definitionsRetrieved == 1)

        let request = CreateDatasetDefinition(
            info: "test",
            device: underlying.selfDevice,
            redundantCopies: 1,
            existingVersions: .init(policy: .all, duration: SecondsDuration(3)),
            removedVersions: .init(policy: .all, duration: SecondsDuration(3))
        )
        _ = try await client.createDatasetDefinition(request: request)

        for _ in 0..<3 { _ = try await client.datasetDefinitions() }
        for _ in 0..<3 { _ = try await client.datasetDefinition(definition: definition) }
        #expect(await definitions.hits == 6)
        #expect(await definitions.misses == 1)
        #expect(await underlying.calls.definitionCreated == 1)
        #expect(await underlying.calls.definitionRetrieved == 2)
        #expect(await underlying.calls.definitionsRetrieved == 1)
    }

    @Test("updates dataset definitions and invalidates existing cache")
    func updateInvalidatesCache() async throws {
        let underlying = MockServerApiEndpointClient()
        let definitions = TrackingCache(underlying: MapCache<DatasetDefinitionId, DatasetDefinition>())
        let client = makeClient(underlying: underlying, datasetDefinitionsCache: definitions)

        for _ in 0..<3 { _ = try await client.datasetDefinitions() }
        #expect(await definitions.hits == 0)
        #expect(await definitions.misses == 0)

        let definition = UUID()
        for _ in 0..<3 { _ = try await client.datasetDefinition(definition: definition) }
        #expect(await definitions.hits == 3)
        #expect(await definitions.misses == 1)

        let request = UpdateDatasetDefinition(
            info: "test",
            redundantCopies: 1,
            existingVersions: .init(policy: .all, duration: SecondsDuration(3)),
            removedVersions: .init(policy: .all, duration: SecondsDuration(3))
        )
        try await client.updateDatasetDefinition(definition: definition, request: request)

        for _ in 0..<3 { _ = try await client.datasetDefinitions() }
        for _ in 0..<3 { _ = try await client.datasetDefinition(definition: definition) }
        #expect(await definitions.hits == 6)
        #expect(await definitions.misses == 1)
        #expect(await underlying.calls.definitionUpdated == 1)
        #expect(await underlying.calls.definitionRetrieved == 2)
        #expect(await underlying.calls.definitionsRetrieved == 1)
    }

    @Test("deletes dataset definitions and invalidates existing cache")
    func deleteInvalidatesCache() async throws {
        let underlying = MockServerApiEndpointClient()
        let definitions = TrackingCache(underlying: MapCache<DatasetDefinitionId, DatasetDefinition>())
        let client = makeClient(underlying: underlying, datasetDefinitionsCache: definitions)

        for _ in 0..<3 { _ = try await client.datasetDefinitions() }
        let definition = UUID()
        for _ in 0..<3 { _ = try await client.datasetDefinition(definition: definition) }
        #expect(await definitions.hits == 3)
        #expect(await definitions.misses == 1)

        try await client.deleteDatasetDefinition(definition: definition)

        for _ in 0..<3 { _ = try await client.datasetDefinitions() }
        for _ in 0..<3 { _ = try await client.datasetDefinition(definition: definition) }
        #expect(await definitions.hits == 6)
        #expect(await definitions.misses == 2)
        #expect(await underlying.calls.definitionDeleted == 1)
        #expect(await underlying.calls.definitionRetrieved == 2)
        #expect(await underlying.calls.definitionsRetrieved == 1)
    }

    @Test("retrieves dataset entries for a dataset definition")
    func retrievesEntriesForDefinition() async throws {
        let underlying = MockServerApiEndpointClient()
        let entriesForDefinition = TrackingCache(underlying: MapCache<DatasetDefinitionId, DatasetEntriesForDefinition>())
        let client = makeClient(underlying: underlying, datasetEntriesForDefinitionCache: entriesForDefinition)

        for _ in 0..<5 { _ = try await client.datasetEntries(definition: UUID()) }

        #expect(await entriesForDefinition.hits == 5)
        #expect(await entriesForDefinition.misses == 5)
        #expect(await underlying.calls.entriesRetrieved == 5)
    }

    @Test("retrieves and caches individual dataset entries")
    func cachesIndividualEntry() async throws {
        let underlying = MockServerApiEndpointClient()
        let entries = TrackingCache(underlying: MapCache<DatasetEntryId, DatasetEntry>())
        let client = makeClient(underlying: underlying, datasetEntriesCache: entries)

        let entry = UUID()
        _ = try await client.datasetEntry(entry: entry)
        #expect(await entries.hits == 1)
        #expect(await entries.misses == 1)
        #expect(await underlying.calls.entryRetrieved == 1)

        for _ in 0..<5 { _ = try await client.datasetEntry(entry: entry) }
        #expect(await entries.hits == 6)
        #expect(await entries.misses == 1)
        #expect(await underlying.calls.entryRetrieved == 1)
    }

    @Test("retrieves and caches latest dataset entry (until == nil)")
    func cachesLatestEntry() async throws {
        let underlying = MockServerApiEndpointClient()
        let entriesForDefinition = TrackingCache(underlying: MapCache<DatasetDefinitionId, DatasetEntriesForDefinition>())
        let client = makeClient(underlying: underlying, datasetEntriesForDefinitionCache: entriesForDefinition)

        let definition = UUID()
        _ = try await client.latestEntry(definition: definition, until: nil)
        #expect(await entriesForDefinition.hits == 1)
        #expect(await entriesForDefinition.misses == 1)
        #expect(await underlying.calls.entryLatestRetrieved == 0)
        #expect(await underlying.calls.entriesRetrieved == 1)

        for _ in 0..<5 { _ = try await client.latestEntry(definition: definition, until: nil) }
        #expect(await entriesForDefinition.hits == 6)
        #expect(await entriesForDefinition.misses == 1)
        #expect(await underlying.calls.entryLatestRetrieved == 0)
        #expect(await underlying.calls.entriesRetrieved == 1)
    }

    @Test("does not cache latest dataset entry (until is provided)")
    func doesNotCacheLatestWithUntil() async throws {
        let underlying = MockServerApiEndpointClient()
        let client = makeClient(underlying: underlying)

        let definition = UUID()
        let until = Date()
        for _ in 0..<5 { _ = try await client.latestEntry(definition: definition, until: until) }

        #expect(await underlying.calls.entryLatestRetrieved == 5)
        #expect(await underlying.calls.entriesRetrieved == 0)
    }

    @Test("creates dataset entries and invalidates cached summary")
    func createsDatasetEntries() async throws {
        let underlying = MockServerApiEndpointClient()
        let entriesForDefinition = TrackingCache(underlying: MapCache<DatasetDefinitionId, DatasetEntriesForDefinition>())
        let client = makeClient(underlying: underlying, datasetEntriesForDefinitionCache: entriesForDefinition)

        let definition = UUID()
        for _ in 0..<3 { _ = try await client.latestEntry(definition: definition, until: nil) }
        #expect(await entriesForDefinition.hits == 3)
        #expect(await entriesForDefinition.misses == 1)

        let request = CreateDatasetEntry(
            definition: definition,
            device: UUID(),
            data: [],
            metadata: UUID(),
            changes: 1,
            size: 2
        )
        _ = try await client.createDatasetEntry(request: request)

        #expect(await entriesForDefinition.hits == 3)
        #expect(await entriesForDefinition.misses == 2)

        for _ in 0..<3 { _ = try await client.latestEntry(definition: definition, until: nil) }
        #expect(await entriesForDefinition.hits == 6)
        #expect(await entriesForDefinition.misses == 2)
        #expect(await underlying.calls.entryCreated == 1)
        #expect(await underlying.calls.entryRetrieved == 1)
        #expect(await underlying.calls.entriesRetrieved == 1)
    }

    @Test("deletes dataset entries and invalidates cached entry")
    func deletesDatasetEntries() async throws {
        let underlying = MockServerApiEndpointClient()
        let entries = TrackingCache(underlying: MapCache<DatasetEntryId, DatasetEntry>())
        let client = makeClient(underlying: underlying, datasetEntriesCache: entries)

        let definition = UUID()

        for _ in 0..<2 { _ = try await client.latestEntry(definition: definition, until: nil) }
        #expect(await entries.hits == 6)
        #expect(await entries.misses == 0)

        try await client.deleteDatasetEntry(entry: UUID())

        #expect(await entries.hits == 6)
        #expect(await entries.misses == 1)

        for _ in 0..<3 { _ = try await client.latestEntry(definition: definition, until: nil) }
        #expect(await entries.hits == 15)
        #expect(await entries.misses == 1)
        #expect(await underlying.calls.entryDeleted == 1)
        #expect(await underlying.calls.entriesRetrieved == 1)
    }

    @Test("retrieves public schedules (without caching)")
    func publicSchedulesWithoutCaching() async throws {
        let underlying = MockServerApiEndpointClient()
        let client = makeClient(underlying: underlying)

        for _ in 0..<5 { _ = try await client.publicSchedules() }
        #expect(await underlying.calls.publicSchedulesRetrieved == 5)
    }

    @Test("retrieves individual public schedules (without caching)")
    func publicScheduleWithoutCaching() async throws {
        let underlying = MockServerApiEndpointClient()
        let client = makeClient(underlying: underlying)

        let schedule = UUID()
        for _ in 0..<5 { _ = try await client.publicSchedule(schedule: schedule) }
        #expect(await underlying.calls.publicScheduleRetrieved == 5)
    }

    @Test("retrieves and caches dataset metadata (with entry ID)")
    func cachesMetadataWithEntryId() async throws {
        let underlying = MockServerApiEndpointClient()
        let client = makeClient(underlying: underlying)

        let entry = UUID()
        _ = try await client.datasetMetadata(entry: entry)
        #expect(await underlying.calls.metadataWithIdRetrieved == 1)
        #expect(await underlying.calls.metadataWithEntryRetrieved == 0)

        for _ in 0..<5 { _ = try await client.datasetMetadata(entry: entry) }
        #expect(await underlying.calls.metadataWithIdRetrieved == 1)
        #expect(await underlying.calls.metadataWithEntryRetrieved == 0)
    }

    @Test("retrieves and caches dataset metadata (with entry)")
    func cachesMetadataWithEntry() async throws {
        let underlying = MockServerApiEndpointClient()
        let client = makeClient(underlying: underlying)

        let entry = TestGenerators.entry()
        _ = try await client.datasetMetadata(entry: entry)
        #expect(await underlying.calls.metadataWithEntryRetrieved == 1)
        #expect(await underlying.calls.metadataWithIdRetrieved == 0)

        for _ in 0..<5 { _ = try await client.datasetMetadata(entry: entry) }
        #expect(await underlying.calls.metadataWithEntryRetrieved == 1)
        #expect(await underlying.calls.metadataWithIdRetrieved == 0)
    }

    @Test("retrieves current user (without caching)")
    func userWithoutCaching() async throws {
        let underlying = MockServerApiEndpointClient()
        let client = makeClient(underlying: underlying)
        for _ in 0..<5 { _ = try await client.user() }
        #expect(await underlying.calls.userRetrieved == 5)
    }

    @Test("resets the current user's salt (without caching)")
    func resetSaltWithoutCaching() async throws {
        let underlying = MockServerApiEndpointClient()
        let client = makeClient(underlying: underlying)
        for _ in 0..<3 { _ = try await client.resetUserSalt() }
        #expect(await underlying.calls.userSaltReset == 3)
    }

    @Test("updates the current user's password (without caching)")
    func resetPasswordWithoutCaching() async throws {
        let underlying = MockServerApiEndpointClient()
        let client = makeClient(underlying: underlying)
        for _ in 0..<3 { try await client.resetUserPassword(request: ResetUserPassword(rawPassword: "x")) }
        #expect(await underlying.calls.userPasswordUpdated == 3)
    }

    @Test("retrieves current device (without caching)")
    func deviceWithoutCaching() async throws {
        let underlying = MockServerApiEndpointClient()
        let client = makeClient(underlying: underlying)
        for _ in 0..<5 { _ = try await client.device() }
        #expect(await underlying.calls.deviceRetrieved == 5)
    }

    @Test("pushes current device key (without caching)")
    func pushDeviceKeyWithoutCaching() async throws {
        let underlying = MockServerApiEndpointClient()
        let client = makeClient(underlying: underlying)
        for _ in 0..<3 { try await client.pushDeviceKey(key: Data("k".utf8)) }
        #expect(await underlying.calls.deviceKeyPushed == 3)
    }

    @Test("pulls current device key (without caching)")
    func pullDeviceKeyWithoutCaching() async throws {
        let underlying = MockServerApiEndpointClient()
        let client = makeClient(underlying: underlying)
        for _ in 0..<3 { _ = try await client.pullDeviceKey() }
        #expect(await underlying.calls.deviceKeyPulled == 3)
    }

    @Test("checks if a device key exists (without caching)")
    func deviceKeyExistsWithoutCaching() async throws {
        let underlying = MockServerApiEndpointClient()
        let client = makeClient(underlying: underlying)
        for _ in 0..<5 { _ = try await client.deviceKeyExists() }
        #expect(await underlying.calls.deviceKeyExistsChecked == 5)
    }

    @Test("makes ping requests (without caching)")
    func pingWithoutCaching() async throws {
        let underlying = MockServerApiEndpointClient()
        let client = makeClient(underlying: underlying)
        for _ in 0..<5 { _ = try await client.ping() }
        #expect(await underlying.calls.pinged == 5)
    }

    @Test("retrieves commands (without caching)")
    func commandsWithoutCaching() async throws {
        let underlying = MockServerApiEndpointClient()
        let client = makeClient(underlying: underlying)
        for _ in 0..<5 { _ = try await client.commands(lastSequenceId: nil) }
        #expect(await underlying.calls.commandsRetrieved == 5)
    }

    @Test("sends analytics entries (without caching)")
    func analyticsWithoutCaching() async throws {
        let underlying = MockServerApiEndpointClient()
        let client = makeClient(underlying: underlying)
        let entry = AnalyticsEntry.collected(.init(app: NoApplicationInformation()))
        for _ in 0..<3 { try await client.sendAnalyticsEntry(entry) }
        #expect(await underlying.calls.analyticsEntriesSent == 3)
    }
}
