import Foundation
@testable import StasisClient
import StasisClientLib
import StasisClientLibTestSupport
import SwiftData
import Testing

@MainActor
@Suite("CacheStatsModel")
struct CacheStatsModelTests {
    @Test("refresh exposes only the public schedules cache when session has no caches bundle")
    func refreshWithoutCachesBundle() async throws {
        let session = try TestSession.make()
        let scheduler = try makeScheduler()
        let model = CacheStatsModel(session: session, scheduler: scheduler)

        await model.refresh()

        #expect(model.isLoading == false)
        #expect(model.stats.map(\.name) == ["Public Schedules"])
    }

    @Test("refresh exposes all caches when the session has a caches bundle")
    func refreshWithCachesBundle() async throws {
        let bundle = makeBundle()
        let session = try TestSession.make(caches: bundle)
        let scheduler = try makeScheduler()
        let model = CacheStatsModel(session: session, scheduler: scheduler)

        await model.refresh()

        let names = model.stats.map(\.name)
        #expect(names == [
            "Dataset Definitions",
            "Dataset Entries",
            "Entries per Definition",
            "Dataset Metadata",
            "Public Schedules"
        ])
    }

    @Test("refresh reflects entries, hits and misses tracked by the underlying caches")
    func refreshReflectsActivity() async throws {
        let bundle = makeBundle()
        let definition = TestGenerators.definition()
        try await bundle.datasetDefinitions.put(definition.id, definition)
        _ = await bundle.datasetDefinitions.get(definition.id)
        _ = await bundle.datasetDefinitions.get(UUID())
        let session = try TestSession.make(caches: bundle)
        let scheduler = try makeScheduler()
        let model = CacheStatsModel(session: session, scheduler: scheduler)

        await model.refresh()

        let definitionsStat = try #require(model.stats.first(where: { $0.name == "Dataset Definitions" }))
        #expect(definitionsStat.entries == 1)
        #expect(definitionsStat.hits == 1)
        #expect(definitionsStat.misses == 1)
    }

    private func makeBundle() -> AuthenticatedSession.CachesBundle {
        AuthenticatedSession.CachesBundle(
            datasetDefinitions: TrackingCache(underlying: MapCache()),
            datasetEntries: TrackingCache(underlying: MapCache()),
            datasetEntriesForDefinition: TrackingCache(underlying: MapCache()),
            datasetMetadata: TrackingCache(underlying: MapCache())
        )
    }

    private func makeScheduler() throws -> BackgroundScheduler {
        let container = try PersistenceSchema.inMemoryContainer()
        return BackgroundScheduler(
            activeScheduleRepository: ActiveScheduleRepository(modelContainer: container),
            localScheduleRepository: LocalScheduleRepository(modelContainer: container),
            ruleRepository: RuleRepository(modelContainer: container),
            executor: MockOperationExecutor(),
            notifications: MockSchedulingNotifications(),
            publicSchedulesLoader: { [] },
            taskScheduler: MockBackgroundTaskScheduler()
        )
    }
}
