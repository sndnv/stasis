import Foundation

public final class DefaultServerMonitor: ServerMonitor {
    public static let unreachableIntervalReduction: Int = 10

    private let initialDelay: TimeInterval
    private let interval: TimeInterval
    private let api: any ServerApiEndpointClient
    private let tracker: any ServerTracker
    private let task: Task<Void, Never>

    public init(
        initialDelay: TimeInterval,
        interval: TimeInterval,
        api: any ServerApiEndpointClient,
        tracker: any ServerTracker
    ) throws {
        guard interval > 0 else {
            throw InvalidArgumentError("interval must be positive")
        }
        guard initialDelay >= 0 else {
            throw InvalidArgumentError("initialDelay must not be negative")
        }
        self.initialDelay = initialDelay
        self.interval = interval
        self.api = api
        self.tracker = tracker

        self.task = Task { [initialDelay, interval, api, tracker] in
            try? await Task.sleep(nanoseconds: Intervals.nanoseconds(initialDelay))
            while !Task.isCancelled {
                do {
                    _ = try await api.ping()
                    await tracker.reachable(server: api.server)
                    try await Task.sleep(nanoseconds: Intervals.nanoseconds(Intervals.fuzzy(interval)))
                } catch is CancellationError {
                    return
                } catch {
                    await tracker.unreachable(server: api.server)
                    let reduced = max(
                        Intervals.fuzzy(interval / Double(Self.unreachableIntervalReduction)),
                        initialDelay
                    )
                    try? await Task.sleep(nanoseconds: Intervals.nanoseconds(reduced))
                }
            }
        }
    }

    public func stop() async {
        task.cancel()
        _ = await task.value
    }
}
