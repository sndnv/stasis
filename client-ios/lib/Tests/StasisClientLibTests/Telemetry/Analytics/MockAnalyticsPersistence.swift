import Foundation
@testable import StasisClientLib

actor MockAnalyticsPersistence: AnalyticsPersistence {
    private let existing: Result<AnalyticsEntry?, Error>
    private var cachedEntries: [AnalyticsEntry] = []
    private var transmittedEntries: [AnalyticsEntry] = []
    private var lastCachedTime: Date = .distantPast
    private var lastTransmittedTime: Date = .distantPast

    init(existing: Result<AnalyticsEntry?, Error>) {
        self.existing = existing
    }

    func cache(_ entry: AnalyticsEntry) async {
        lastCachedTime = Date()
        cachedEntries.append(entry)
    }

    func transmit(_ entry: AnalyticsEntry) async -> Result<Void, Error> {
        lastTransmittedTime = Date()
        transmittedEntries.append(entry)
        return .success(())
    }

    func restore() async -> Result<AnalyticsEntry?, Error> {
        existing
    }

    var lastCached: Date { lastCachedTime }
    var lastTransmitted: Date { lastTransmittedTime }

    var cached: [AnalyticsEntry] { cachedEntries }
    var transmitted: [AnalyticsEntry] { transmittedEntries }
}
