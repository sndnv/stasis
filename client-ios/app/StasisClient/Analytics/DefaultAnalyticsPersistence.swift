import Foundation
import OSLog
import StasisClientLib
import Synchronization

public final class DefaultAnalyticsPersistence: AnalyticsPersistence, @unchecked Sendable {
    private static let logger = Logger(subsystem: "stasis.client.ios", category: "DefaultAnalyticsPersistence")

    private let preferences: UserDefaults
    private let clientProvider: @Sendable () -> any AnalyticsClient
    private let lastCachedState: Mutex<Date>
    private let lastTransmittedState: Mutex<Date>

    public init(preferences: UserDefaults, client: @escaping @Sendable () -> any AnalyticsClient) {
        self.preferences = preferences
        self.clientProvider = client
        self.lastCachedState = Mutex(Date(timeIntervalSince1970: 0))
        self.lastTransmittedState = Mutex(Date(timeIntervalSince1970: 0))
    }

    public func cache(_ entry: AnalyticsEntry) async {
        do {
            let serialized = try serialize(entry: entry)
            preferences.putAnalyticsCachedEntry(serialized)
            lastCachedState.withLock { $0 = Date() }
        } catch {
            Self.logger.error("failed to cache analytics entry: \(error.localizedDescription)")
        }
    }

    public func transmit(_ entry: AnalyticsEntry) async -> Result<Void, any Error> {
        let collected = entry.asCollected()
        let withoutEvents = preferences.analyticsKeepEvents() ? collected : collected.discardEvents()
        let outgoing = preferences.analyticsKeepFailures() ? withoutEvents : withoutEvents.discardFailures()

        do {
            try await clientProvider().sendAnalyticsEntry(.collected(outgoing))
            lastTransmittedState.withLock { $0 = Date() }
            return .success(())
        } catch {
            return .failure(error)
        }
    }

    public func restore() async -> Result<AnalyticsEntry?, any Error> {
        guard let raw = preferences.analyticsCachedEntry() else {
            return .success(nil)
        }
        do {
            let stored = try deserialize(raw)
            lastCachedState.withLock { $0 = max($0, stored.lastCached) }
            lastTransmittedState.withLock { $0 = max($0, stored.lastTransmitted) }
            return .success(.collected(stored.entry.asEntry().asCollected()))
        } catch {
            return .failure(error)
        }
    }

    public var lastCached: Date {
        get async { lastCachedState.withLock { $0 } }
    }

    public var lastTransmitted: Date {
        get async { lastTransmittedState.withLock { $0 } }
    }

    public struct StoredAnalyticsEntry: Codable, Equatable, Sendable {
        public let entry: AnalyticsEntry.AsJson
        public let lastCached: Date
        public let lastTransmitted: Date

        public init(entry: AnalyticsEntry.AsJson, lastCached: Date, lastTransmitted: Date) {
            self.entry = entry
            self.lastCached = lastCached
            self.lastTransmitted = lastTransmitted
        }
    }

    public func serialize(entry: AnalyticsEntry) throws -> String {
        let stored = StoredAnalyticsEntry(
            entry: entry.asJson(),
            lastCached: lastCachedState.withLock { $0 },
            lastTransmitted: lastTransmittedState.withLock { $0 }
        )
        return try serialize(stored: stored)
    }

    public func serialize(stored: StoredAnalyticsEntry) throws -> String {
        let data = try Self.encoder.encode(stored)
        // swiftlint:disable:next optional_data_string_conversion
        return String(decoding: data, as: UTF8.self)
    }

    public func deserialize(_ raw: String) throws -> StoredAnalyticsEntry {
        try Self.decoder.decode(StoredAnalyticsEntry.self, from: Data(raw.utf8))
    }

    private static let encoder: JSONEncoder = {
        let encoder = JSONEncoder()
        encoder.dateEncodingStrategy = .custom { date, encoder in
            var container = encoder.singleValueContainer()
            try container.encode(Int64(date.timeIntervalSince1970 * 1000))
        }
        return encoder
    }()

    private static let decoder: JSONDecoder = {
        let decoder = JSONDecoder()
        decoder.dateDecodingStrategy = .custom { decoder in
            let container = try decoder.singleValueContainer()
            let millis = try container.decode(Int64.self)
            return Date(timeIntervalSince1970: TimeInterval(millis) / 1000.0)
        }
        return decoder
    }()
}

public extension AnalyticsEntry.AsJson {
    func asEntry() -> AnalyticsEntry { .asJson(self) }
}
