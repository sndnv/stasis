import Foundation
@testable import StasisClient
import StasisClientLib
import Testing

@Suite("DefaultServerTracker")
struct DefaultServerTrackerTests {
    @Test("tracks server reachable / unreachable events")
    func trackServerEvents() async {
        let tracker = DefaultServerTracker()

        #expect(await tracker.snapshot().isEmpty)

        await tracker.reachable(server: "test-server-01")
        await tracker.reachable(server: "test-server-02")
        await tracker.unreachable(server: "test-server-01")

        let states = await tracker.snapshot().mapValues(\.reachable)
        #expect(states == ["test-server-01": false, "test-server-02": true])
    }

    @Test("provides per-server update streams")
    func providesUpdates() async {
        let tracker = DefaultServerTracker()
        #expect(await tracker.snapshot().isEmpty)

        await tracker.reachable(server: "test-server-01")

        let updates = await tracker.updates(server: "test-server-01")
        await tracker.reachable(server: "test-server-02")
        await tracker.unreachable(server: "test-server-01")

        var observed: [Bool] = []
        for await state in updates {
            observed.append(state.reachable)
            if state.reachable == false { break }
        }
        #expect(observed.contains(true))
        #expect(observed.last == false)
    }
}
