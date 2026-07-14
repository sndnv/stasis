import Foundation

public protocol AnalyticsPersistence: Sendable {
    func cache(_ entry: AnalyticsEntry) async
    func transmit(_ entry: AnalyticsEntry) async -> Result<Void, Error>
    func restore() async -> Result<AnalyticsEntry?, Error>

    func cachePending(_ entries: [AnalyticsEntry]) async
    func restorePending() async -> Result<[AnalyticsEntry], Error>

    var lastCached: Date { get async }
    var lastTransmitted: Date { get async }
}
