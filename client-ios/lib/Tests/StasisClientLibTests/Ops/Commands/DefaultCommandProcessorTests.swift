import Foundation
@testable import StasisClientLib
import StasisClientLibTestSupport
import Synchronization
import Testing

@Suite("DefaultCommandProcessor")
struct DefaultCommandProcessorTests {
    private let defaultInterval: TimeInterval = 0.1

    @Test("retrieves commands periodically")
    func retrievesPeriodically() async throws {
        try await withRetry {
            let mockApi = MockServerApiEndpointClient()
            let handlers = MockCommandHandlers()

            let processor = try DefaultCommandProcessor(
                initialDelay: 0,
                interval: defaultInterval,
                api: mockApi,
                handlers: handlers
            )

            try await runManaged(processor) {
                await waitUntil { await mockApi.calls.commandsRetrieved >= 1 }
                let first = handlers.snapshot
                try expectRetryable(first.persistCalls >= 1, "persistCalls >= 1, got \(first.persistCalls)")
                try expectRetryable(first.retrieveCalls >= 1, "retrieveCalls >= 1, got \(first.retrieveCalls)")
                try expectRetryable(first.executeCalls >= 1, "executeCalls >= 1, got \(first.executeCalls)")
                try expectRetryable(first.lastSequenceId == 3, "lastSequenceId == 3, got \(first.lastSequenceId)")

                await waitUntil { await mockApi.calls.commandsRetrieved >= 2 }
                let second = handlers.snapshot
                try expectRetryable(second.persistCalls == 1, "persistCalls == 1, got \(second.persistCalls)")
                try expectRetryable(second.retrieveCalls >= 2, "retrieveCalls >= 2, got \(second.retrieveCalls)")
                try expectRetryable(second.executeCalls == 1, "executeCalls == 1, got \(second.executeCalls)")
                try expectRetryable(second.lastSequenceId == 3, "lastSequenceId == 3, got \(second.lastSequenceId)")
            }
        }
    }

    @Test("handles command retrieval failures")
    func handlesRetrievalFailures() async throws {
        try await withRetry {
            let mockApi = MockServerApiEndpointClient(commandsDisabled: true)
            let handlers = MockCommandHandlers()

            let processor = try DefaultCommandProcessor(
                initialDelay: 0,
                interval: defaultInterval,
                api: mockApi,
                handlers: handlers
            )

            try await runManaged(processor) {
                await waitUntil { handlers.snapshot.retrieveCalls >= 3 }
                let snapshot = handlers.snapshot
                try expectRetryable(snapshot.persistCalls == 0, "persistCalls == 0, got \(snapshot.persistCalls)")
                try expectRetryable(snapshot.retrieveCalls >= 3, "retrieveCalls >= 3, got \(snapshot.retrieveCalls)")
                try expectRetryable(snapshot.executeCalls == 0, "executeCalls == 0, got \(snapshot.executeCalls)")
                try expectRetryable(snapshot.lastSequenceId == 0, "lastSequenceId == 0, got \(snapshot.lastSequenceId)")
            }
        }
    }

    @Test("supports retrieving all commands")
    func supportsAll() async throws {
        try await withRetry {
            let mockApi = MockServerApiEndpointClient()
            let handlers = MockCommandHandlers()

            let processor = try DefaultCommandProcessor(
                initialDelay: 0,
                interval: defaultInterval,
                api: mockApi,
                handlers: handlers
            )

            try await runManaged(processor) {
                let commands = try await processor.all()
                try expectRetryable(commands.count == 3, "commands.count == 3, got \(commands.count)")

                try expectRetryable(await mockApi.calls.commandsRetrieved >= 1, "commandsRetrieved >= 1")
                let afterAll = handlers.snapshot
                try expectRetryable(afterAll.persistCalls == 1, "persistCalls == 1, got \(afterAll.persistCalls)")
                try expectRetryable(afterAll.retrieveCalls >= 1, "retrieveCalls >= 1, got \(afterAll.retrieveCalls)")
                try expectRetryable(afterAll.executeCalls == 1, "executeCalls == 1, got \(afterAll.executeCalls)")
                try expectRetryable(afterAll.lastSequenceId == 3, "lastSequenceId == 3, got \(afterAll.lastSequenceId)")

                await waitUntil { await mockApi.calls.commandsRetrieved >= 2 }
                let afterWait = handlers.snapshot
                try expectRetryable(afterWait.persistCalls == 1, "persistCalls == 1, got \(afterWait.persistCalls)")
                try expectRetryable(afterWait.retrieveCalls >= 2, "retrieveCalls >= 2, got \(afterWait.retrieveCalls)")
                try expectRetryable(afterWait.executeCalls == 1, "executeCalls == 1, got \(afterWait.executeCalls)")
                try expectRetryable(afterWait.lastSequenceId == 3, "lastSequenceId == 3, got \(afterWait.lastSequenceId)")
            }
        }
    }

    @Test("handles failures when retrieving all commands")
    func handlesAllFailures() async throws {
        try await withRetry {
            let mockApi = MockServerApiEndpointClient(commandsDisabled: true)
            let handlers = MockCommandHandlers()

            let processor = try DefaultCommandProcessor(
                initialDelay: 0,
                interval: defaultInterval,
                api: mockApi,
                handlers: handlers
            )

            try await runManaged(processor) {
                await #expect(throws: EndpointFailure.self) {
                    _ = try await processor.all()
                }

                let afterAll = handlers.snapshot
                try expectRetryable(afterAll.persistCalls == 0, "persistCalls == 0, got \(afterAll.persistCalls)")
                try expectRetryable(afterAll.executeCalls == 0, "executeCalls == 0, got \(afterAll.executeCalls)")
                try expectRetryable(afterAll.lastSequenceId == 0, "lastSequenceId == 0, got \(afterAll.lastSequenceId)")

                await waitUntil { handlers.snapshot.retrieveCalls >= 3 }
                let afterWait = handlers.snapshot
                try expectRetryable(afterWait.persistCalls == 0, "persistCalls == 0, got \(afterWait.persistCalls)")
                try expectRetryable(afterWait.retrieveCalls >= 3, "retrieveCalls >= 3, got \(afterWait.retrieveCalls)")
                try expectRetryable(afterWait.executeCalls == 0, "executeCalls == 0, got \(afterWait.executeCalls)")
                try expectRetryable(afterWait.lastSequenceId == 0, "lastSequenceId == 0, got \(afterWait.lastSequenceId)")
            }
        }
    }

    @Test("supports retrieving latest commands")
    func supportsLatest() async throws {
        try await withRetry {
            let mockApi = MockServerApiEndpointClient()
            let handlers = MockCommandHandlers()

            let processor = try DefaultCommandProcessor(
                initialDelay: 0,
                interval: defaultInterval,
                api: mockApi,
                handlers: handlers
            )

            try await runManaged(processor) {
                let first = try await processor.latest()
                try expectRetryable(first.count == 3, "first.count == 3, got \(first.count)")

                try expectRetryable(await mockApi.calls.commandsRetrieved >= 1, "commandsRetrieved >= 1")
                let afterFirst = handlers.snapshot
                try expectRetryable(afterFirst.persistCalls == 1, "persistCalls == 1, got \(afterFirst.persistCalls)")
                try expectRetryable(afterFirst.retrieveCalls >= 1, "retrieveCalls >= 1, got \(afterFirst.retrieveCalls)")
                try expectRetryable(afterFirst.executeCalls == 1, "executeCalls == 1, got \(afterFirst.executeCalls)")
                try expectRetryable(afterFirst.lastSequenceId == 3, "lastSequenceId == 3, got \(afterFirst.lastSequenceId)")

                let second = try await processor.latest()
                try expectRetryable(second.isEmpty, "second.isEmpty, got count \(second.count)")

                try expectRetryable(await mockApi.calls.commandsRetrieved >= 2, "commandsRetrieved >= 2")
                let afterSecond = handlers.snapshot
                try expectRetryable(afterSecond.persistCalls == 1, "persistCalls == 1, got \(afterSecond.persistCalls)")
                try expectRetryable(afterSecond.retrieveCalls >= 2, "retrieveCalls >= 2, got \(afterSecond.retrieveCalls)")
                try expectRetryable(afterSecond.executeCalls == 1, "executeCalls == 1, got \(afterSecond.executeCalls)")
                try expectRetryable(afterSecond.lastSequenceId == 3, "lastSequenceId == 3, got \(afterSecond.lastSequenceId)")

                await waitUntil { await mockApi.calls.commandsRetrieved >= 3 }
                let afterWait = handlers.snapshot
                try expectRetryable(afterWait.persistCalls == 1, "persistCalls == 1, got \(afterWait.persistCalls)")
                try expectRetryable(afterWait.retrieveCalls >= 3, "retrieveCalls >= 3, got \(afterWait.retrieveCalls)")
                try expectRetryable(afterWait.executeCalls == 1, "executeCalls == 1, got \(afterWait.executeCalls)")
                try expectRetryable(afterWait.lastSequenceId == 3, "lastSequenceId == 3, got \(afterWait.lastSequenceId)")
            }
        }
    }

    @Test("handles failures when retrieving latest commands")
    func handlesLatestFailures() async throws {
        try await withRetry {
            let mockApi = MockServerApiEndpointClient(commandsDisabled: true)
            let handlers = MockCommandHandlers()

            let processor = try DefaultCommandProcessor(
                initialDelay: 0,
                interval: defaultInterval,
                api: mockApi,
                handlers: handlers
            )

            try await runManaged(processor) {
                await #expect(throws: EndpointFailure.self) {
                    _ = try await processor.latest()
                }

                let afterCall = handlers.snapshot
                try expectRetryable(afterCall.persistCalls == 0, "persistCalls == 0, got \(afterCall.persistCalls)")
                try expectRetryable(afterCall.retrieveCalls >= 1, "retrieveCalls >= 1, got \(afterCall.retrieveCalls)")
                try expectRetryable(afterCall.executeCalls == 0, "executeCalls == 0, got \(afterCall.executeCalls)")
                try expectRetryable(afterCall.lastSequenceId == 0, "lastSequenceId == 0, got \(afterCall.lastSequenceId)")

                await waitUntil { handlers.snapshot.retrieveCalls >= 3 }
                let afterWait = handlers.snapshot
                try expectRetryable(afterWait.persistCalls == 0, "persistCalls == 0, got \(afterWait.persistCalls)")
                try expectRetryable(afterWait.retrieveCalls >= 3, "retrieveCalls >= 3, got \(afterWait.retrieveCalls)")
                try expectRetryable(afterWait.executeCalls == 0, "executeCalls == 0, got \(afterWait.executeCalls)")
                try expectRetryable(afterWait.lastSequenceId == 0, "lastSequenceId == 0, got \(afterWait.lastSequenceId)")
            }
        }
    }

    @Test("supports stopping itself")
    func supportsStopping() async throws {
        try await withRetry {
            let mockApi = MockServerApiEndpointClient()
            let handlers = MockCommandHandlers()

            let processor = try DefaultCommandProcessor(
                initialDelay: 0,
                interval: defaultInterval,
                api: mockApi,
                handlers: handlers
            )

            await waitUntil { await mockApi.calls.commandsRetrieved >= 1 }
            try expectRetryable(await mockApi.calls.commandsRetrieved >= 1, "at least one retrieval before stop")

            await processor.stop()

            let snapshot = await mockApi.calls.commandsRetrieved
            try await Task.sleep(nanoseconds: UInt64(defaultInterval * 2_000_000_000))

            let afterStop = await mockApi.calls.commandsRetrieved
            try expectRetryable(
                afterStop == snapshot,
                "commandsRetrieved stable at \(snapshot), got \(afterStop)"
            )
        }
    }

    @Test("rejects invalid configuration")
    func rejectsInvalidConfiguration() {
        let mockApi = MockServerApiEndpointClient()
        let handlers = MockCommandHandlers()

        #expect(throws: InvalidArgumentError.self) {
            _ = try DefaultCommandProcessor(
                initialDelay: 0, interval: 0, api: mockApi, handlers: handlers
            )
        }
        #expect(throws: InvalidArgumentError.self) {
            _ = try DefaultCommandProcessor(
                initialDelay: -1, interval: 0.1, api: mockApi, handlers: handlers
            )
        }
    }

    private func runManaged(
        _ processor: DefaultCommandProcessor,
        _ block: () async throws -> Void
    ) async throws {
        do {
            try await block()
            await processor.stop()
        } catch {
            await processor.stop()
            throw error
        }
    }
}

private final class MockCommandHandlers: CommandHandlers {
    struct Snapshot: Sendable, Equatable {
        var persistCalls: Int = 0
        var retrieveCalls: Int = 0
        var executeCalls: Int = 0
        var lastSequenceId: Int64 = 0
    }

    private let state = Mutex<Snapshot>(Snapshot())

    var snapshot: Snapshot { state.withLock { $0 } }

    func persistLastProcessedCommand(sequenceId: Int64) async {
        state.withLock { snapshot in
            snapshot.persistCalls += 1
            snapshot.lastSequenceId = sequenceId
        }
    }

    func retrieveLastProcessedCommand() async -> Int64 {
        state.withLock { snapshot in
            snapshot.retrieveCalls += 1
            return snapshot.lastSequenceId
        }
    }

    func executeCommands(commands: [CommandAsJson]) async -> Int64? {
        state.withLock { snapshot in
            snapshot.executeCalls += 1
        }
        return commands.map(\.sequenceId).max() ?? 0
    }
}
