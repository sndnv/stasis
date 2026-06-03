import Foundation
import Synchronization

public final class DefaultCommandProcessor: CommandProcessor {
    public static let failureIntervalReduction: Int = 10

    private let initialDelay: TimeInterval
    private let interval: TimeInterval
    private let api: any ServerApiEndpointClient
    private let handlers: any CommandHandlers
    private let task = Mutex<Task<Void, Never>?>(nil)

    public init(
        initialDelay: TimeInterval,
        interval: TimeInterval,
        api: any ServerApiEndpointClient,
        handlers: any CommandHandlers
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
        self.handlers = handlers
        reschedule(after: initialDelay)
    }

    public func all() async throws -> [CommandAsJson] {
        cancelTask()
        do {
            let commands = try await api.commands(lastSequenceId: nil)
            let last = await handlers.retrieveLastProcessedCommand()
            await Self.process(commands.filter { $0.sequenceId > last }, handlers: handlers)
            reschedule(after: Intervals.fuzzy(interval))
            return commands
        } catch {
            reschedule(after: reducedInterval())
            throw error
        }
    }

    public func latest() async throws -> [CommandAsJson] {
        cancelTask()
        do {
            let last = await handlers.retrieveLastProcessedCommand()
            let commands = try await api.commands(lastSequenceId: last)
            await Self.process(commands, handlers: handlers)
            reschedule(after: Intervals.fuzzy(interval))
            return commands
        } catch {
            reschedule(after: reducedInterval())
            throw error
        }
    }

    public func stop() async {
        cancelTask()
    }

    private func cancelTask() {
        task.withLock { current in
            current?.cancel()
            current = nil
        }
    }

    private func reschedule(after delay: TimeInterval) {
        task.withLock { [api, handlers, interval, initialDelay] current in
            current?.cancel()
            current = Task<Void, Never> {
                try? await Task.sleep(nanoseconds: Intervals.nanoseconds(delay))
                while !Task.isCancelled {
                    do {
                        let last = await handlers.retrieveLastProcessedCommand()
                        let commands = try await api.commands(lastSequenceId: last)
                        await Self.process(commands, handlers: handlers)
                        try await Task.sleep(nanoseconds: Intervals.nanoseconds(Intervals.fuzzy(interval)))
                    } catch is CancellationError {
                        return
                    } catch {
                        let reduced = max(
                            Intervals.fuzzy(interval / Double(Self.failureIntervalReduction)),
                            initialDelay
                        )
                        try? await Task.sleep(nanoseconds: Intervals.nanoseconds(reduced))
                    }
                }
            }
        }
    }

    private func reducedInterval() -> TimeInterval {
        max(Intervals.fuzzy(interval / Double(Self.failureIntervalReduction)), initialDelay)
    }

    private static func process(_ commands: [CommandAsJson], handlers: any CommandHandlers) async {
        guard !commands.isEmpty else { return }
        if let last = await handlers.executeCommands(commands: commands) {
            await handlers.persistLastProcessedCommand(sequenceId: last)
        }
    }
}
