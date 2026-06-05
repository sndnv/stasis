import Foundation
import StasisClientLib

public protocol ServerTrackerView: ServerTracker {
    func snapshot() async -> [String: ServerState]
    func updates() async -> AsyncStream<[String: ServerState]>
    func updates(server: String) async -> AsyncStream<ServerState>
}

public actor DefaultServerTracker: ServerTrackerView {
    private var state: [String: ServerState] = [:]
    private var subscribers: [UUID: AsyncStream<[String: ServerState]>.Continuation] = [:]

    public init() {}

    public func reachable(server: String) {
        state[server] = ServerState(reachable: true, timestamp: Date())
        broadcast()
    }

    public func unreachable(server: String) {
        state[server] = ServerState(reachable: false, timestamp: Date())
        broadcast()
    }

    public func snapshot() -> [String: ServerState] {
        state
    }

    public func updates() -> AsyncStream<[String: ServerState]> {
        subscribe()
    }

    public func updates(server: String) -> AsyncStream<ServerState> {
        let stream = subscribe()
        return AsyncStream { continuation in
            let task = Task {
                for await states in stream {
                    if let value = states[server] {
                        continuation.yield(value)
                    }
                }
                continuation.finish()
            }
            continuation.onTermination = { _ in task.cancel() }
        }
    }

    private func subscribe() -> AsyncStream<[String: ServerState]> {
        let id = UUID()
        return AsyncStream { continuation in
            subscribers[id] = continuation
            continuation.yield(state)
            continuation.onTermination = { [weak self] _ in
                Task { await self?.unsubscribe(id) }
            }
        }
    }

    private func unsubscribe(_ id: UUID) {
        subscribers.removeValue(forKey: id)
    }

    private func broadcast() {
        for continuation in subscribers.values {
            continuation.yield(state)
        }
    }
}
