public protocol AnalyticsClient: Sendable {
    func sendAnalyticsEntry(_ entry: AnalyticsEntry) async throws
}
