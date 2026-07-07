import Foundation
import OSLog
import StasisClientLib

public protocol BackupTrackerView: BackupTracker {
    func snapshot() async -> [OperationId: BackupState]
    func updates() async -> AsyncStream<[OperationId: BackupState]>
    func updates(operation: OperationId) async -> AsyncStream<BackupState>
    func remove(operation: OperationId) async
    func clear() async
}

public actor DefaultBackupTracker: BackupTrackerView {
    public static let maxRetention: TimeInterval = 30 * 24 * 60 * 60
    public static let persistAfterEvents: Int = 1000
    public static let persistAfterPeriod: TimeInterval = 30

    private static let logger = Logger(subsystem: "stasis.client.ios", category: "DefaultBackupTracker")

    private let store: StateStore<[OperationId: BackupState]>
    private var state: [OperationId: BackupState] = [:]
    private var subscribers: [UUID: AsyncStream<[OperationId: BackupState]>.Continuation] = [:]
    private var pendingUpdates: Int = 0
    private var persistTask: Task<Void, Never>?
    private var startupTask: Task<Void, Never>?

    public init(store: StateStore<[OperationId: BackupState]>) {
        self.store = store
    }

    public func started(operation: OperationId, definition: DatasetDefinitionId) async {
        await waitForStart()
        applyStart(operation: operation, definition: definition)
        await afterUpdate(isCompleted: false)
    }

    public func entityDiscovered(operation: OperationId, entity: EntityRef) async {
        await mutate(operation: operation) { $0.entityDiscovered(entity: entity) }
    }

    public func specificationProcessed(operation: OperationId, unmatched: [(Rule, any Error)]) async {
        guard !unmatched.isEmpty else { return }
        let formatted = unmatched.map { "Rule [\($0.0.asString())] failed with [\($0.1.localizedDescription)]" }
        await mutate(operation: operation) { $0.specificationProcessed(unmatched: formatted) }
    }

    public func entityExamined(operation: OperationId, entity: EntityRef) async {
        await mutate(operation: operation) { $0.entityExamined(entity: entity) }
    }

    public func entitySkipped(operation: OperationId, entity: EntityRef) async {
        await mutate(operation: operation) { $0.entitySkipped(entity: entity) }
    }

    public func entityCollected(operation: OperationId, entity: SourceEntity) async {
        await mutate(operation: operation) { $0.entityCollected(entity: entity) }
    }

    public func entityProcessingStarted(operation: OperationId, entity: EntityRef, expectedParts: Int) async {
        await mutate(operation: operation) { $0.entityProcessingStarted(entity: entity, expectedParts: expectedParts) }
    }

    public func entityPartProcessed(operation: OperationId, entity: EntityRef) async {
        await mutate(operation: operation) { $0.entityPartProcessed(entity: entity) }
    }

    public func entityProcessed(
        operation: OperationId, entity: EntityRef, metadata: Either<EntityMetadata, EntityMetadata>
    ) async {
        await mutate(operation: operation) { $0.entityProcessed(entity: entity, metadata: metadata) }
    }

    public func metadataCollected(operation: OperationId) async {
        await mutate(operation: operation) { $0.backupMetadataCollected() }
    }

    public func metadataPushed(operation: OperationId, entry: DatasetEntryId) async {
        await mutate(operation: operation) { $0.backupMetadataPushed() }
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
        existing = existing.backupCompleted()
        applyUpdate(operation: operation, updated: existing)
        await afterUpdate(isCompleted: true)
    }

    public func stateOf(operation: OperationId) async -> BackupState? {
        await waitForStart()
        return state[operation]
    }

    public func snapshot() async -> [OperationId: BackupState] {
        await waitForStart()
        return state
    }

    public func updates() async -> AsyncStream<[OperationId: BackupState]> {
        await waitForStart()
        return subscribe()
    }

    public func updates(operation: OperationId) async -> AsyncStream<BackupState> {
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
            Self.logger.error("failed to restore backup tracker state: \(error.localizedDescription)")
        }
    }

    private func applyStart(operation: OperationId, definition: DatasetDefinitionId) {
        let fresh = BackupState.start(operation: operation, definition: definition)
        applyUpdate(operation: operation, updated: fresh)
    }

    private func mutate(
        operation: OperationId,
        _ transform: (BackupState) -> BackupState
    ) async {
        await waitForStart()
        guard let existing = state[operation] else {
            Self.logger.error("dropped event for unknown operation [\(operation)]")
            return
        }
        applyUpdate(operation: operation, updated: transform(existing))
        await afterUpdate(isCompleted: false)
    }

    private func applyUpdate(operation: OperationId, updated: BackupState) {
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

    private func subscribe() -> AsyncStream<[OperationId: BackupState]> {
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
            Self.logger.error("failed to persist backup tracker state: \(error.localizedDescription)")
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
