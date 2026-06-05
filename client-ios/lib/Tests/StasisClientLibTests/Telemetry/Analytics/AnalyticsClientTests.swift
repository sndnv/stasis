@testable import StasisClientLib
import Testing

@Suite("NoOpAnalyticsClient")
struct NoOpAnalyticsClientTests {
    @Test("accepts any entry without throwing")
    func acceptsAnyEntry() async throws {
        let client = NoOpAnalyticsClient()
        let collected = AnalyticsEntry.Collected(app: NoApplicationInformation())
            .withEvent(name: "event", attributes: [:])
            .withFailure(message: "failure")

        try await client.sendAnalyticsEntry(.collected(collected))
        try await client.sendAnalyticsEntry(.asJson(AnalyticsEntry.collected(collected).asJson()))
    }
}
