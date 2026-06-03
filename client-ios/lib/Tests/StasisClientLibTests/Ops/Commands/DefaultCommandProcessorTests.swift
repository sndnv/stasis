import Foundation
@testable import StasisClientLib
import Synchronization
import Testing

@Suite("DefaultCommandProcessor")
struct DefaultCommandProcessorTests {
    private let defaultInterval: TimeInterval = 0.1

    @Test("retrieves commands periodically")
    func retrievesPeriodically() async throws {
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
            #expect(handlers.snapshot.persistCalls >= 1)
            #expect(handlers.snapshot.retrieveCalls >= 1)
            #expect(handlers.snapshot.executeCalls >= 1)
            #expect(handlers.snapshot.lastSequenceId == 3)

            await waitUntil { await mockApi.calls.commandsRetrieved >= 2 }
            #expect(handlers.snapshot.persistCalls == 1)
            #expect(handlers.snapshot.retrieveCalls >= 2)
            #expect(handlers.snapshot.executeCalls == 1)
            #expect(handlers.snapshot.lastSequenceId == 3)
        }
    }

    @Test("handles command retrieval failures")
    func handlesRetrievalFailures() async throws {
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
            #expect(handlers.snapshot.persistCalls == 0)
            #expect(handlers.snapshot.retrieveCalls >= 3)
            #expect(handlers.snapshot.executeCalls == 0)
            #expect(handlers.snapshot.lastSequenceId == 0)
        }
    }

    @Test("supports retrieving all commands")
    func supportsAll() async throws {
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
            #expect(commands.count == 3)

            #expect(await mockApi.calls.commandsRetrieved >= 1)
            #expect(handlers.snapshot.persistCalls == 1)
            #expect(handlers.snapshot.retrieveCalls >= 1)
            #expect(handlers.snapshot.executeCalls == 1)
            #expect(handlers.snapshot.lastSequenceId == 3)

            await waitUntil { await mockApi.calls.commandsRetrieved >= 2 }
            #expect(handlers.snapshot.persistCalls == 1)
            #expect(handlers.snapshot.retrieveCalls >= 2)
            #expect(handlers.snapshot.executeCalls == 1)
            #expect(handlers.snapshot.lastSequenceId == 3)
        }
    }

    @Test("handles failures when retrieving all commands")
    func handlesAllFailures() async throws {
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

            #expect(handlers.snapshot.persistCalls == 0)
            #expect(handlers.snapshot.executeCalls == 0)
            #expect(handlers.snapshot.lastSequenceId == 0)

            await waitUntil { handlers.snapshot.retrieveCalls >= 3 }
            #expect(handlers.snapshot.persistCalls == 0)
            #expect(handlers.snapshot.retrieveCalls >= 3)
            #expect(handlers.snapshot.executeCalls == 0)
            #expect(handlers.snapshot.lastSequenceId == 0)
        }
    }

    @Test("supports retrieving latest commands")
    func supportsLatest() async throws {
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
            #expect(first.count == 3)

            #expect(await mockApi.calls.commandsRetrieved >= 1)
            #expect(handlers.snapshot.persistCalls == 1)
            #expect(handlers.snapshot.retrieveCalls >= 1)
            #expect(handlers.snapshot.executeCalls == 1)
            #expect(handlers.snapshot.lastSequenceId == 3)

            let second = try await processor.latest()
            #expect(second.isEmpty)

            #expect(await mockApi.calls.commandsRetrieved >= 2)
            #expect(handlers.snapshot.persistCalls == 1)
            #expect(handlers.snapshot.retrieveCalls >= 2)
            #expect(handlers.snapshot.executeCalls == 1)
            #expect(handlers.snapshot.lastSequenceId == 3)

            await waitUntil { await mockApi.calls.commandsRetrieved >= 3 }
            #expect(handlers.snapshot.persistCalls == 1)
            #expect(handlers.snapshot.retrieveCalls >= 3)
            #expect(handlers.snapshot.executeCalls == 1)
            #expect(handlers.snapshot.lastSequenceId == 3)
        }
    }

    @Test("handles failures when retrieving latest commands")
    func handlesLatestFailures() async throws {
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

            #expect(handlers.snapshot.persistCalls == 0)
            #expect(handlers.snapshot.retrieveCalls >= 1)
            #expect(handlers.snapshot.executeCalls == 0)
            #expect(handlers.snapshot.lastSequenceId == 0)

            await waitUntil { handlers.snapshot.retrieveCalls >= 3 }
            #expect(handlers.snapshot.persistCalls == 0)
            #expect(handlers.snapshot.retrieveCalls >= 3)
            #expect(handlers.snapshot.executeCalls == 0)
            #expect(handlers.snapshot.lastSequenceId == 0)
        }
    }

    @Test("supports stopping itself")
    func supportsStopping() async throws {
        let mockApi = MockServerApiEndpointClient()
        let handlers = MockCommandHandlers()

        let processor = try DefaultCommandProcessor(
            initialDelay: 0,
            interval: defaultInterval,
            api: mockApi,
            handlers: handlers
        )

        await waitUntil { await mockApi.calls.commandsRetrieved >= 1 }

        await processor.stop()

        let snapshot = await mockApi.calls.commandsRetrieved
        try await Task.sleep(nanoseconds: UInt64(defaultInterval * 2_000_000_000))

        #expect(await mockApi.calls.commandsRetrieved == snapshot)
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
