import Foundation
import OSLog
import StasisClientLib

public protocol RecoveryTrackerView: RecoveryTracker {
    func snapshot() async -> [OperationId: RecoveryState]
    func updates() async -> AsyncStream<[OperationId: RecoveryState]>
    func updates(operation: OperationId) async -> AsyncStream<RecoveryState>
    func remove(operation: OperationId) async
    func clear() async
}

public actor DefaultRecoveryTracker: RecoveryTrackerView {
    public static let maxRetention: TimeInterval = 30 * 24 * 60 * 60
    public static let persistAfterEvents: Int = 1000
    public static let persistAfterPeriod: TimeInterval = 30

    private static let logger = Logger(subsystem: "stasis.client.ios", category: "DefaultRecoveryTracker")

    private let store: StateStore<[OperationId: RecoveryState]>
    private var state: [OperationId: RecoveryState] = [:]
    private var subscribers: [UUID: AsyncStream<[OperationId: RecoveryState]>.Continuation] = [:]
    private var pendingUpdates: Int = 0
    private var persistTask: Task<Void, Never>?
    private var startupTask: Task<Void, Never>?

    public init(store: StateStore<[OperationId: RecoveryState]>) {
        self.store = store
    }

    public func started(operation: OperationId) async {
        await waitForStart()
        applyUpdate(operation: operation, updated: RecoveryState.start(operation: operation))
        await afterUpdate(isCompleted: false)
    }

    public func entityExamined(
        operation: OperationId, entity: EntityRef, metadataChanged: Bool, contentChanged: Bool
    ) async {
        await mutate(operation: operation) { $0.entityExamined(entity: entity) }
    }

    public func entityCollected(operation: OperationId, entity: TargetEntity) async {
        await mutate(operation: operation) { $0.entityCollected(entity: entity) }
    }

    public func entityProcessingStarted(operation: OperationId, entity: EntityRef, expectedParts: Int) async {
        await mutate(operation: operation) { $0.entityProcessingStarted(entity: entity, expectedParts: expectedParts) }
    }

    public func entityPartProcessed(operation: OperationId, entity: EntityRef) async {
        await mutate(operation: operation) { $0.entityPartProcessed(entity: entity) }
    }

    public func entityProcessed(operation: OperationId, entity: EntityRef) async {
        await mutate(operation: operation) { $0.entityProcessed(entity: entity) }
    }

    public func metadataApplied(operation: OperationId, entity: EntityRef) async {
        await mutate(operation: operation) { $0.entityMetadataApplied(entity: entity) }
    }

    public func failureEncountered(operation: OperationId, failure: any Error) async {
        await mutate(operation: operation) { $0.failureEncountered(failure: failure) }
    }

    public func failureEncountered(operation: OperationId, entity: EntityRef, failure: any Error) async {
        await mutate(operation: operation) { $0.entityFailed(entity: entity, reason: failure) }
    }

    public func completed(operation: OperationId) async {
        await waitForStart()
        guard var existing = state[operation] else {
            Self.logger.error("dropped completed for unknown operation [\(operation)]")
            return
        }
        existing = existing.recoveryCompleted()
        applyUpdate(operation: operation, updated: existing)
        await afterUpdate(isCompleted: true)
    }

    public func stateOf(operation: OperationId) async -> RecoveryState? {
        await waitForStart()
        return state[operation]
    }

    public func snapshot() async -> [OperationId: RecoveryState] {
        await waitForStart()
        return state
    }

    public func updates() async -> AsyncStream<[OperationId: RecoveryState]> {
        await waitForStart()
        return subscribe()
    }

    public func updates(operation: OperationId) async -> AsyncStream<RecoveryState> {
        let stream = await updates()
        return AsyncStream { continuation in
            let task = Task {
                for await states in stream {
                    if let value = states[operation] {
                        continuation.yield(value)
                    }
                }
                continuation.finish()
            }
            continuation.onTermination = { _ in task.cancel() }
        }
    }

    public func remove(operation: OperationId) async {
        await waitForStart()
        state.removeValue(forKey: operation)
        broadcast()
        await persistNow()
    }

    public func clear() async {
        await waitForStart()
        state.removeAll()
        broadcast()
        await persistNow()
    }

    private func waitForStart() async {
        if startupTask == nil {
            startupTask = Task { [weak self] in await self?.restoreFromStore() }
        }
        await startupTask?.value
    }

    private func restoreFromStore() async {
        do {
            if let restored = try await store.restore() {
                state = restored
                broadcast()
            }
        } catch {
            Self.logger.error("failed to restore recovery tracker state: \(error.localizedDescription)")
        }
    }

    private func mutate(
        operation: OperationId,
        _ transform: (RecoveryState) -> RecoveryState
    ) async {
        await waitForStart()
        guard let existing = state[operation] else {
            Self.logger.error("dropped event for unknown operation [\(operation)]")
            return
        }
        applyUpdate(operation: operation, updated: transform(existing))
        await afterUpdate(isCompleted: false)
    }

    private func applyUpdate(operation: OperationId, updated: RecoveryState) {
        let now = Date()
        state = state.filter { $0.value.started.addingTimeInterval(Self.maxRetention) > now }
        state[operation] = updated
        broadcast()
    }

    private func afterUpdate(isCompleted: Bool) async {
        pendingUpdates += 1
        if isCompleted || pendingUpdates >= Self.persistAfterEvents {
            await persistNow()
        } else if persistTask == nil {
            schedulePersist()
        }
    }

    private func subscribe() -> AsyncStream<[OperationId: RecoveryState]> {
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

    private func persistNow() async {
        persistTask?.cancel()
        persistTask = nil
        let snapshot = state
        pendingUpdates = 0
        do {
            try await store.persist(snapshot)
        } catch {
            Self.logger.error("failed to persist recovery tracker state: \(error.localizedDescription)")
        }
    }

    private func schedulePersist() {
        persistTask = Task { [weak self] in
            try? await Task.sleep(nanoseconds: UInt64(Self.persistAfterPeriod * 1_000_000_000))
            guard !Task.isCancelled else { return }
            await self?.persistNow()
        }
    }
}
