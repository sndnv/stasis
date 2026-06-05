import Foundation
@testable import StasisClient
import StasisClientLib
import Testing

@Suite("DefaultAnalyticsCollector")
struct DefaultAnalyticsCollectorTests {
    @Test("records events with optional attributes")
    func recordEvents() async {
        let collector = DefaultAnalyticsCollector(
            app: NoApplicationInformation(),
            persistenceInterval: 60,
            transmissionInterval: 60,
            persistence: MockAnalyticsPersistence()
        )

        await collector.recordEvent(name: "test_event")
        await collector.recordEvent(name: "test_event", attributes: ["a": "b"])
        await collector.recordEvent(name: "test_event", attributes: ["a": "b", "c": "d"])
        await collector.recordEvent(name: "test_event", attributes: ["a": "b"])

        await eventually {
            guard case .success(let state) = await collector.state() else { return false }
            let events = state.events
            return events.count == 4
                && events[0].id == 0 && events[0].event == "test_event"
                && events[1].id == 1 && events[1].event == "test_event{a='b'}"
                && events[2].id == 2 && events[2].event == "test_event{a='b',c='d'}"
                && events[3].id == 3 && events[3].event == "test_event{a='b'}"
        }
    }

    @Test("records failures (anonymized error type and message)")
    func recordFailures() async {
        let persistence = MockAnalyticsPersistence(lastTransmittedOverride: Date())
        let collector = DefaultAnalyticsCollector(
            app: NoApplicationInformation(),
            persistenceInterval: 60,
            transmissionInterval: 60,
            persistence: persistence
        )

        await collector.recordFailure(TestFailure(message: "Test failure"))
        await collector.recordFailure(message: "Other failure")

        await eventually {
            guard case .success(let state) = await collector.state() else { return false }
            return state.failures.count == 2
                && state.failures[0].message == "TestFailure - Test failure"
                && state.failures[1].message == "Other failure"
        }
    }

    @Test("loads cached state on first access")
    func supportLoadingCacheState() async {
        let existing = AnalyticsEntry.Collected(app: NoApplicationInformation())
            .withEvent(name: "existing_event", attributes: [:])
            .withFailure(message: "Existing failure")
        let persistence = MockAnalyticsPersistence(existing: .success(.collected(existing)))

        let collector = DefaultAnalyticsCollector(
            app: NoApplicationInformation(),
            persistenceInterval: 60,
            transmissionInterval: 60,
            persistence: persistence
        )

        await collector.recordEvent(name: "test_event")

        await eventually {
            guard case .success(let state) = await collector.state() else { return false }
            let events = state.events
            let cachedCount = await persistence.cached.count
            let transmittedCount = await persistence.transmitted.count
            return events.count == 2
                && events[0].id == 0 && events[0].event == "existing_event"
                && events[1].id == 1 && events[1].event == "test_event"
                && cachedCount == 0
                && transmittedCount == 0
        }
    }

    @Test("ignores cache restore failures")
    func handleFailuresWhenLoadingCachedState() async {
        let persistence = MockAnalyticsPersistence(existing: .failure(TestFailure(message: "Test failure")))
        let collector = DefaultAnalyticsCollector(
            app: NoApplicationInformation(),
            persistenceInterval: 60,
            transmissionInterval: 60,
            persistence: persistence
        )

        await eventually {
            guard case .success(let state) = await collector.state() else { return false }
            let cachedCount = await persistence.cached.count
            let transmittedCount = await persistence.transmitted.count
            return state.events.isEmpty
                && state.failures.isEmpty
                && cachedCount == 0
                && transmittedCount == 0
        }
    }

    @Test("caches state locally on the persistence interval")
    func supportCachingStateLocally() async {
        let persistence = MockAnalyticsPersistence(lastTransmittedOverride: Date())
        let collector = DefaultAnalyticsCollector(
            app: NoApplicationInformation(),
            persistenceInterval: 0.1,
            transmissionInterval: 60,
            persistence: persistence
        )

        await collector.recordEvent(name: "test_event")
        try? await Task.sleep(nanoseconds: 75_000_000)
        await collector.recordEvent(name: "test_event", attributes: ["a": "b"])
        try? await Task.sleep(nanoseconds: 75_000_000)
        await collector.recordEvent(name: "test_event", attributes: ["a": "b", "c": "d"])
        try? await Task.sleep(nanoseconds: 75_000_000)
        await collector.recordEvent(name: "test_event", attributes: ["a": "b"])
        try? await Task.sleep(nanoseconds: 150_000_000)
        await collector.recordFailure(message: "Test failure")

        await eventually {
            guard case .success(let state) = await collector.state() else { return false }
            let cached = await persistence.cached
            let transmittedCount = await persistence.transmitted.count
            guard cached.count == 3 else { return false }
            return state.events.count == 4
                && state.failures.count == 1
                && cached[0].events.count == 2 && cached[0].failures.isEmpty
                && cached[1].events.count == 4 && cached[1].failures.isEmpty
                && cached[2].events.count == 4 && cached[2].failures.count == 1
                && transmittedCount == 0
        }
    }

    @Test("transmits state remotely after a failure (and clears local copy)")
    func supportTransmittingStateRemotely() async {
        let persistence = MockAnalyticsPersistence()
        let collector = DefaultAnalyticsCollector(
            app: NoApplicationInformation(),
            persistenceInterval: 60,
            transmissionInterval: 60,
            persistence: persistence
        )

        await collector.recordEvent(name: "test_event")
        await collector.recordFailure(message: "Test failure")

        await eventually {
            guard case .success(let state) = await collector.state() else { return false }
            let cached = await persistence.cached
            let transmitted = await persistence.transmitted
            return state.events.isEmpty
                && state.failures.isEmpty
                && cached.count == 1
                && cached[0].events.isEmpty
                && cached[0].failures.isEmpty
                && transmitted.count == 1
                && transmitted[0].events.count == 1
                && transmitted[0].failures.count == 1
        }
    }

    @Test("retains state on transmission failure")
    func handleTransmissionFailures() async {
        let persistence = MockAnalyticsPersistence(transmitMode: .failure(TestFailure(message: "Test failure")))
        let collector = DefaultAnalyticsCollector(
            app: NoApplicationInformation(),
            persistenceInterval: 60,
            transmissionInterval: 60,
            persistence: persistence
        )

        await collector.recordEvent(name: "test_event")
        await collector.recordFailure(message: "Test failure")

        await eventually {
            guard case .success(let state) = await collector.state() else { return false }
            let cached = await persistence.cached
            let transmitted = await persistence.transmitted
            return state.events.count == 1
                && state.failures.count == 1
                && cached.count == 1
                && cached[0].events.count == 1
                && cached[0].failures.count == 1
                && transmitted.isEmpty
        }
    }

    @Test("transmits state on demand")
    func supportTransmittingStateRemotelyOnDemand() async {
        let persistence = MockAnalyticsPersistence(lastTransmittedOverride: Date())
        let collector = DefaultAnalyticsCollector(
            app: NoApplicationInformation(),
            persistenceInterval: 60,
            transmissionInterval: 60,
            persistence: persistence
        )

        await collector.recordFailure(message: "Test failure")

        await eventually {
            guard case .success(let state) = await collector.state() else { return false }
            let cached = await persistence.cached
            return state.events.isEmpty
                && state.failures.count == 1
                && cached.count == 1
                && cached[0].events.isEmpty
                && cached[0].failures.count == 1
        }

        #expect(await persistence.transmitted.isEmpty)

        await collector.send()

        await eventually {
            let transmitted = await persistence.transmitted
            return transmitted.count == 1
                && transmitted[0].events.isEmpty
                && transmitted[0].failures.count == 1
        }
    }

    private struct TestFailure: LocalizedError {
        let message: String
        var errorDescription: String? { message }
    }
}
