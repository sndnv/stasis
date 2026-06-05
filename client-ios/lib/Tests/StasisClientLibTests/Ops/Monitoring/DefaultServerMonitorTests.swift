import Foundation
@testable import StasisClientLib
import StasisClientLibTestSupport
import Testing

@Suite("DefaultServerMonitor")
struct DefaultServerMonitorTests {
    private let defaultInterval: TimeInterval = 0.1

    @Test("pings the server periodically")
    func pingsPeriodically() async throws {
        let mockApi = MockServerApiEndpointClient()
        let mockTracker = MockServerTracker()

        let monitor = try DefaultServerMonitor(
            initialDelay: 0,
            interval: defaultInterval,
            api: mockApi,
            tracker: mockTracker
        )

        try await runManaged(monitor) {
            await waitUntil { await mockApi.calls.pinged >= 1 }
            #expect((mockTracker.statistics[.serverReachable] ?? 0) >= 1)
            #expect(mockTracker.statistics[.serverUnreachable] == 0)

            await waitUntil { await mockApi.calls.pinged >= 2 }
            #expect((mockTracker.statistics[.serverReachable] ?? 0) >= 2)
            #expect(mockTracker.statistics[.serverUnreachable] == 0)
        }
    }

    @Test("handles ping failures")
    func handlesFailures() async throws {
        let mockApi = MockServerApiEndpointClient(pingDisabled: true)
        let mockTracker = MockServerTracker()

        let monitor = try DefaultServerMonitor(
            initialDelay: 0,
            interval: defaultInterval / 2,
            api: mockApi,
            tracker: mockTracker
        )

        try await runManaged(monitor) {
            await waitUntil { (mockTracker.statistics[.serverUnreachable] ?? 0) >= 3 }
            #expect(mockTracker.statistics[.serverReachable] == 0)
            #expect((mockTracker.statistics[.serverUnreachable] ?? 0) >= 3)
        }
    }

    @Test("supports stopping itself")
    func supportsStopping() async throws {
        let mockApi = MockServerApiEndpointClient()
        let mockTracker = MockServerTracker()

        let monitor = try DefaultServerMonitor(
            initialDelay: 0,
            interval: defaultInterval / 2,
            api: mockApi,
            tracker: mockTracker
        )

        await waitUntil { (mockTracker.statistics[.serverReachable] ?? 0) >= 1 }
        #expect(mockTracker.statistics[.serverUnreachable] == 0)

        await monitor.stop()

        let snapshot = mockTracker.statistics[.serverReachable] ?? 0
        try await Task.sleep(nanoseconds: UInt64(defaultInterval * 2_000_000_000))

        #expect((mockTracker.statistics[.serverReachable] ?? 0) == snapshot)
        #expect(mockTracker.statistics[.serverUnreachable] == 0)
    }

    @Test("rejects invalid configuration")
    func rejectsInvalidConfiguration() {
        let mockApi = MockServerApiEndpointClient()
        let mockTracker = MockServerTracker()

        #expect(throws: InvalidArgumentError.self) {
            _ = try DefaultServerMonitor(
                initialDelay: 0, interval: 0, api: mockApi, tracker: mockTracker
            )
        }
        #expect(throws: InvalidArgumentError.self) {
            _ = try DefaultServerMonitor(
                initialDelay: -1, interval: 0.1, api: mockApi, tracker: mockTracker
            )
        }
    }

    private func runManaged(
        _ monitor: DefaultServerMonitor,
        _ block: () async throws -> Void
    ) async throws {
        do {
            try await block()
            await monitor.stop()
        } catch {
            await monitor.stop()
            throw error
        }
    }
}
