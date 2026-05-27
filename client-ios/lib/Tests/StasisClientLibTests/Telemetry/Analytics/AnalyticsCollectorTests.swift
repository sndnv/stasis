@testable import StasisClientLib
import Testing

@Suite("NoOpAnalyticsCollector")
struct NoOpAnalyticsCollectorTests {
    @Test("records nothing")
    func recordsNothing() async {
        let collector = NoOpAnalyticsCollector()

        await collector.recordEvent(name: "test_event")

        let success: Result<Void, Error> = .success(())
        await collector.recordEvent(name: "test_event", result: success)
        await collector.recordEvent(name: "test_event", attributes: ["a": "b"])

        await collector.recordFailure(message: "Other failure")
        await collector.send()

        #expect(await collector.persistence == nil)

        guard case .success(let state) = await collector.state() else {
            Issue.record("Expected success state but got failure")
            return
        }
        #expect(state.events.isEmpty)
        #expect(state.failures.isEmpty)
    }
}
