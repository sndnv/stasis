import Foundation
import OSLog
import StasisClientLib

public actor DefaultAnalyticsCollector: AnalyticsCollector {
    private static let logger = Logger(subsystem: "stasis.client.ios", category: "DefaultAnalyticsCollector")

    public let persistence: (any AnalyticsPersistence)?

    private let app: any ApplicationInformation
    private let persistenceInterval: TimeInterval
    private let transmissionInterval: TimeInterval
    private var latest: AnalyticsEntry.Collected
    private var pending: [AnalyticsEntry.Collected] = []
    private var persistTask: Task<Void, Never>?
    private var startupTask: Task<Void, Never>?
    private var inflightPersist: Task<Void, Never>?

    public init(
        app: any ApplicationInformation,
        persistenceInterval: TimeInterval,
        transmissionInterval: TimeInterval,
        persistence: any AnalyticsPersistence
    ) {
        self.app = app
        self.persistenceInterval = persistenceInterval
        self.transmissionInterval = transmissionInterval
        self.persistence = persistence
        self.latest = AnalyticsEntry.Collected(app: app)
    }

    public func recordEvent(name: String, attributes: [String: String]) async {
        await waitForStart()
        latest = latest.withEvent(name: name, attributes: attributes)
        scheduleNextPersist()
    }

    public func recordFailure(message: String) async {
        await waitForStart()
        latest = latest.withFailure(message: message)
        cancelScheduledPersist()
        await persistState(forceTransmit: false)
    }

    public func state() async -> Result<AnalyticsEntry, any Error> {
        await waitForStart()
        return .success(.collected(latest))
    }

    public func send() async {
        await waitForStart()
        await persistState(forceTransmit: true)
    }

    private func waitForStart() async {
        if startupTask == nil {
            startupTask = Task { [weak self] in await self?.loadState() }
        }
        await startupTask?.value
    }

    private func loadState() async {
        guard let persistence else { return }

        let restoredPending: [AnalyticsEntry.Collected]
        switch await persistence.restorePending() {
        case .success(let entries): restoredPending = entries.map { $0.asCollected() }
        case .failure: restoredPending = []
        }

        switch await persistence.restore() {
        case .success(let entry):
            guard let restored = entry?.asCollected() else {
                latest = AnalyticsEntry.Collected(app: app)
                pending = restoredPending
                return
            }

            if restored.runtime.app == app.asString() {
                latest = restored
                pending = restoredPending
            } else {
                let fresh = AnalyticsEntry.Collected(app: app)
                latest = fresh
                pending = restoredPending + [restored]
                await persistence.cache(.collected(fresh))
                await persistence.cachePending(pending.map { .collected($0) })
            }
        case .failure(let error):
            Self.logger.error("failed to restore analytics state: \(error.localizedDescription)")
            latest = AnalyticsEntry.Collected(app: app)
            pending = restoredPending
        }
    }

    private func persistState(forceTransmit: Bool) async {
        persistTask?.cancel()
        persistTask = nil
        let previous = inflightPersist
        let task: Task<Void, Never> = Task { [weak self] in
            await previous?.value
            await self?.runPersist(forceTransmit: forceTransmit)
        }
        inflightPersist = task
        await task.value
    }

    private func runPersist(forceTransmit: Bool) async {
        guard let persistence else { return }
        let snapshot = latest
        let entry: AnalyticsEntry = .collected(snapshot)
        let lastTransmitted = await persistence.lastTransmitted
        let shouldTransmit = forceTransmit
            || lastTransmitted.addingTimeInterval(transmissionInterval) < Date()

        if shouldTransmit {
            await transmitPending()
            switch await persistence.transmit(entry) {
            case .success:
                dropTransmitted(snapshot)
                await persistence.cache(.collected(latest))
            case .failure(let error):
                Self.logger.debug("transmit failed, caching locally: \(error.localizedDescription)")
                await persistence.cache(.collected(latest))
            }
        } else {
            await persistence.cache(entry)
        }
    }

    private func transmitPending() async {
        guard let persistence, !pending.isEmpty else { return }

        var remaining: [AnalyticsEntry.Collected] = []
        for entry in pending {
            switch await persistence.transmit(.collected(entry)) {
            case .success: break
            case .failure: remaining.append(entry)
            }
        }

        if remaining.count != pending.count {
            pending = remaining
            await persistence.cachePending(pending.map { .collected($0) })
        }
    }

    private func dropTransmitted(_ transmitted: AnalyticsEntry.Collected) {
        let txEvents = transmitted.events.count
        let txFailures = transmitted.failures.count
        if latest.events.count == txEvents && latest.failures.count == txFailures {
            latest = AnalyticsEntry.Collected(app: app)
            return
        }
        let remainingEvents = Array(latest.events.dropFirst(txEvents))
        let remainingFailures = Array(latest.failures.dropFirst(txFailures))
        latest = AnalyticsEntry.Collected(
            runtime: latest.runtime,
            events: remainingEvents.enumerated().map { .init(id: $0.offset, event: $0.element.event) },
            failures: remainingFailures,
            created: latest.created,
            updated: Date()
        )
    }

    private func scheduleNextPersist() {
        guard persistTask == nil else { return }
        persistTask = Task { [weak self, persistenceInterval] in
            try? await Task.sleep(nanoseconds: UInt64(persistenceInterval * 1_000_000_000))
            guard !Task.isCancelled else { return }
            await self?.persistState(forceTransmit: false)
        }
    }

    private func cancelScheduledPersist() {
        persistTask?.cancel()
        persistTask = nil
    }
}
