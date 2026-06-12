import Foundation
import Observation
import StasisClientLib

struct CacheStat: Identifiable, Equatable, Sendable {
    let id: String
    let name: String
    let entries: Int
    let hits: Int64
    let misses: Int64
    let readStatistics: OperationStatistics
    let writeStatistics: OperationStatistics
}

@MainActor
@Observable
final class CacheStatsModel {
    private let caches: AuthenticatedSession.CachesBundle?
    private let publicSchedules: TrackingCache<Int, [Schedule]>

    private(set) var stats: [CacheStat] = []
    private(set) var isLoading: Bool = true

    init(session: AuthenticatedSession?, scheduler: BackgroundScheduler) {
        self.caches = session?.caches
        self.publicSchedules = scheduler.publicSchedulesTracking
    }

    func refresh() async {
        isLoading = true
        var result: [CacheStat] = []
        if let caches {
            result.append(await capture(name: "Dataset Definitions", cache: caches.datasetDefinitions))
            result.append(await capture(name: "Dataset Entries", cache: caches.datasetEntries))
            result.append(await capture(name: "Entries per Definition", cache: caches.datasetEntriesForDefinition))
            result.append(await capture(name: "Dataset Metadata", cache: caches.datasetMetadata))
        }
        result.append(await capture(name: "Public Schedules", cache: publicSchedules))
        stats = result
        isLoading = false
    }

    private func capture<Key, Value>(
        name: String,
        cache: TrackingCache<Key, Value>
    ) async -> CacheStat {
        let entries = await cache.all().count
        let hits = await cache.hits
        let misses = await cache.misses
        let read = await cache.readStatistics
        let write = await cache.writeStatistics
        return CacheStat(
            id: name,
            name: name,
            entries: entries,
            hits: hits,
            misses: misses,
            readStatistics: read,
            writeStatistics: write
        )
    }
}
