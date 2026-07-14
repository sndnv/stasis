import Foundation
@testable import StasisClient
import StasisClientLib
import Testing

@Suite("DefaultAnalyticsPersistence")
struct DefaultAnalyticsPersistenceTests {
    @Test("caches entries locally")
    func cacheEntriesLocally() async throws {
        let defaults = TestDefaults.isolatedDefaults()
        let client = TestAnalyticsClient()
        let persistence = DefaultAnalyticsPersistence(preferences: defaults, client: { client })

        let lastCachedBefore = await persistence.lastCached
        #expect(lastCachedBefore == Date(timeIntervalSince1970: 0))

        let collected = entry
        let expected = try persistence.serialize(entry: .collected(collected))
        await persistence.cache(.collected(collected))

        let lastCachedAfter = await persistence.lastCached
        #expect(lastCachedAfter > Date(timeIntervalSince1970: 0))
        let cachedJson = try #require(defaults.analyticsCachedEntry())
        let cached = try persistence.deserialize(cachedJson)
        let expectedStored = try persistence.deserialize(expected)
        #expect(cached.entry == expectedStored.entry)
    }

    @Test("transmits entries with events and failures when settings allow")
    func transmitEntriesRemotelyWithEventsAndFailures() async throws {
        let defaults = TestDefaults.isolatedDefaults()
        defaults.set(true, forKey: Settings.Keys.analyticsKeepEvents)
        defaults.set(true, forKey: Settings.Keys.analyticsKeepFailures)
        let client = TestAnalyticsClient()
        let persistence = DefaultAnalyticsPersistence(preferences: defaults, client: { client })

        let lastTransmittedBefore = await persistence.lastTransmitted
        #expect(lastTransmittedBefore == Date(timeIntervalSince1970: 0))

        let collected = entry
        let result = await persistence.transmit(.collected(collected))
        try result.get()

        let sent = try #require(await client.lastEntry).asCollected()
        #expect(sent.events.map(\.event) == ["test"])
        #expect(sent.failures.map(\.message) == ["Test failure"])
        #expect(sent.runtime == collected.runtime)
        #expect(sent.created == collected.created)
        let lastTransmittedAfter = await persistence.lastTransmitted
        #expect(lastTransmittedAfter > Date(timeIntervalSince1970: 0))
    }

    @Test("strips events and failures when settings opt out")
    func transmitEntriesRemotelyWithoutEventsAndFailures() async throws {
        let defaults = TestDefaults.isolatedDefaults()
        defaults.set(false, forKey: Settings.Keys.analyticsKeepEvents)
        defaults.set(false, forKey: Settings.Keys.analyticsKeepFailures)
        let client = TestAnalyticsClient()
        let persistence = DefaultAnalyticsPersistence(preferences: defaults, client: { client })

        let collected = entry
        let result = await persistence.transmit(.collected(collected))
        try result.get()

        let sent = try #require(await client.lastEntry).asCollected()
        #expect(sent.events.isEmpty)
        #expect(sent.failures.isEmpty)
        #expect(sent.runtime == collected.runtime)
        #expect(sent.created == collected.created)
    }

    @Test("restores entries from local cache when available")
    func restoreEntriesFromLocalCacheWhenAvailable() async throws {
        let defaults = TestDefaults.isolatedDefaults()
        let client = TestAnalyticsClient()
        let persistence = DefaultAnalyticsPersistence(preferences: defaults, client: { client })

        let collected = entry
        let now = Date()
        let stored = DefaultAnalyticsPersistence.StoredAnalyticsEntry(
            entry: AnalyticsEntry.collected(collected).asJson(),
            lastCached: now,
            lastTransmitted: now
        )
        let serialized = try persistence.serialize(stored: stored)
        defaults.putAnalyticsCachedEntry(serialized)

        let restored = try #require(try (await persistence.restore()).get()).asCollected()
        #expect(restored.events.map(\.event) == ["test"])
        #expect(restored.failures.map(\.message) == ["Test failure"])
        #expect(restored.runtime == collected.runtime)
        #expect(millis(restored.created) == millis(collected.created))
    }

    private func millis(_ date: Date) -> Int64 {
        Int64(date.timeIntervalSince1970 * 1000)
    }

    @Test("caches and restores the pending queue")
    func cacheAndRestorePendingQueue() async throws {
        let defaults = TestDefaults.isolatedDefaults()
        let client = TestAnalyticsClient()
        let persistence = DefaultAnalyticsPersistence(preferences: defaults, client: { client })

        let emptyBefore = try (await persistence.restorePending()).get()
        #expect(emptyBefore.isEmpty)

        let first = AnalyticsEntry.Collected(app: NoApplicationInformation())
            .withEvent(name: "test", attributes: [:])
        let second = AnalyticsEntry.Collected(app: NoApplicationInformation())
            .withEvent(name: "test a", attributes: [:])
        await persistence.cachePending([.collected(first), .collected(second)])

        let restored = try (await persistence.restorePending()).get()
        #expect(restored.count == 2)
        #expect(restored[0].asCollected().events.map(\.event) == ["test"])
        #expect(restored[1].asCollected().events.map(\.event) == ["test a"])
    }

    @Test("returns nil restoring an empty cache")
    func notRestoreEntriesFromLocalCacheWhenNotAvailable() async throws {
        let defaults = TestDefaults.isolatedDefaults()
        let client = TestAnalyticsClient()
        let persistence = DefaultAnalyticsPersistence(preferences: defaults, client: { client })

        let result = await persistence.restore()
        #expect(try result.get() == nil)
    }

    @Test("propagates client failures on transmit")
    func propagatesTransmitFailures() async {
        let defaults = TestDefaults.isolatedDefaults()
        let client = TestAnalyticsClient(failure: TestFailure(message: "boom"))
        let persistence = DefaultAnalyticsPersistence(preferences: defaults, client: { client })

        let result = await persistence.transmit(.collected(entry))
        #expect(throws: TestFailure.self) { _ = try result.get() }
    }

    private var entry: AnalyticsEntry.Collected {
        AnalyticsEntry.Collected(app: NoApplicationInformation())
            .withEvent(name: "test", attributes: [:])
            .withFailure(message: "Test failure")
    }

    private struct TestFailure: LocalizedError, Equatable {
        let message: String
        var errorDescription: String? { message }
    }

    private final actor TestAnalyticsClient: AnalyticsClient {
        private let failure: (any Error)?
        private(set) var lastEntry: AnalyticsEntry?
        private(set) var sent: Int = 0

        init(failure: (any Error)? = nil) {
            self.failure = failure
        }

        func sendAnalyticsEntry(_ entry: AnalyticsEntry) async throws {
            if let failure { throw failure }
            lastEntry = entry
            sent += 1
        }
    }
}
