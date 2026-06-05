public protocol AnalyticsClient: Sendable {
    func sendAnalyticsEntry(_ entry: AnalyticsEntry) async throws
}

public struct NoOpAnalyticsClient: AnalyticsClient {
    public init() {}
    public func sendAnalyticsEntry(_ entry: AnalyticsEntry) async throws {}
}
