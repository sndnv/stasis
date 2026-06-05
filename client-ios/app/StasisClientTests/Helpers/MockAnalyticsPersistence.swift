import Foundation
import StasisClientLib

final actor MockAnalyticsPersistence: AnalyticsPersistence {
    enum TransmitMode: Sendable {
        case success
        case failure(any Error)
    }

    private let existing: Result<AnalyticsEntry?, any Error>
    private let transmitMode: TransmitMode
    private let lastTransmittedOverride: Date?
    private(set) var cached: [AnalyticsEntry] = []
    private(set) var transmitted: [AnalyticsEntry] = []
    private var lastCachedAt: Date = .init(timeIntervalSince1970: 0)
    private var lastTransmittedAt: Date = .init(timeIntervalSince1970: 0)

    init(
        existing: Result<AnalyticsEntry?, any Error> = .success(nil),
        transmitMode: TransmitMode = .success,
        lastTransmittedOverride: Date? = nil
    ) {
        self.existing = existing
        self.transmitMode = transmitMode
        self.lastTransmittedOverride = lastTransmittedOverride
    }

    func cache(_ entry: AnalyticsEntry) async {
        cached.append(entry)
        lastCachedAt = Date()
    }

    func transmit(_ entry: AnalyticsEntry) async -> Result<Void, any Error> {
        switch transmitMode {
        case .success:
            transmitted.append(entry)
            lastTransmittedAt = Date()
            return .success(())
        case .failure(let error):
            return .failure(error)
        }
    }

    func restore() async -> Result<AnalyticsEntry?, any Error> {
        existing
    }

    var lastCached: Date { lastCachedAt }
    var lastTransmitted: Date { lastTransmittedOverride ?? lastTransmittedAt }
}
