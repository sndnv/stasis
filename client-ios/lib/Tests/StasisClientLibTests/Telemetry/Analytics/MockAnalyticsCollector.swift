@testable import StasisClientLib

actor MockAnalyticsCollector: AnalyticsCollector {
    private var entry: AnalyticsEntry.Collected = AnalyticsEntry.Collected(app: NoApplicationInformation())

    init() {}

    func recordEvent(name: String, attributes: [String: String]) async {
        entry = entry.withEvent(name: name, attributes: attributes)
    }

    func recordFailure(message: String) async {
        entry = entry.withFailure(message: message)
    }

    func state() async -> Result<AnalyticsEntry, Error> {
        .success(.collected(entry))
    }

    func send() async {}

    nonisolated var persistence: (any AnalyticsPersistence)? { nil }
}
