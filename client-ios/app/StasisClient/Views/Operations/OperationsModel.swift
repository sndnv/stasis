import Foundation
import Observation
import StasisClientLib

@MainActor
@Observable
final class OperationsModel {
    struct Summary: Identifiable, Equatable, Sendable {
        enum Status: Equatable, Sendable {
            case active
            case completed(Date)
            case stopped

            var isActive: Bool {
                if case .active = self { true } else { false }
            }
        }

        let id: OperationId
        let type: OperationType
        let started: Date
        let progress: OperationProgress
        let status: Status
        let definitionInfo: String?
    }

    private let session: AuthenticatedSession
    private let trackers: DefaultTrackers

    private(set) var operations: [Summary] = []
    private(set) var isLoading: Bool = true
    private(set) var error: String?

    private var backups: [OperationId: BackupState] = [:]
    private var recoveries: [OperationId: RecoveryState] = [:]
    private var activeIds: Set<OperationId> = []
    private var definitionInfo: [DatasetDefinitionId: String] = [:]

    init(session: AuthenticatedSession, trackers: DefaultTrackers) {
        self.session = session
        self.trackers = trackers
    }

    func clearError() { error = nil }

    func refresh() async { await load() }

    func start() async {
        await load()
        await withTaskGroup(of: Void.self) { group in
            group.addTask { await self.observeBackups() }
            group.addTask { await self.observeRecoveries() }
            group.addTask { await self.pollActive() }
        }
    }

    func stop(_ id: OperationId) async {
        do {
            try await session.operationExecutor.stop(operation: id)
            await refreshActive()
        } catch {
            self.error = error.localizedDescription
        }
    }

    func resume(_ id: OperationId, type: OperationType) async {
        guard type == .backup else { return }
        error = nil
        _ = await session.operationExecutor.resumeBackup(operation: id, callback: callback())
        await refreshActive()
    }

    func remove(_ id: OperationId, type: OperationType) async {
        switch type {
        case .backup: await trackers.backup.remove(operation: id)
        case .recovery: await trackers.recovery.remove(operation: id)
        default: break
        }
    }

    private func load() async {
        do {
            let definitions = try await session.serverApiClient.datasetDefinitions()
            definitionInfo = Dictionary(uniqueKeysWithValues: definitions.map { ($0.id, $0.info) })
        } catch {
            self.error = error.localizedDescription
        }
        backups = await trackers.backup.snapshot()
        recoveries = await trackers.recovery.snapshot()
        await refreshActive()
        isLoading = false
    }

    private func observeBackups() async {
        for await update in await trackers.backup.updates() {
            backups = update
            await refreshActive()
        }
    }

    private func observeRecoveries() async {
        for await update in await trackers.recovery.updates() {
            recoveries = update
            await refreshActive()
        }
    }

    private func pollActive() async {
        while !Task.isCancelled {
            try? await Task.sleep(for: .seconds(2))
            guard !Task.isCancelled else { return }
            await refreshActive()
        }
    }

    private func refreshActive() async {
        activeIds = Set(await session.operationExecutor.active().keys)
        recompute()
    }

    private func recompute() {
        let backupSummaries: [Summary] = backups.values.map { state in
            Summary(
                id: state.operation,
                type: .backup,
                started: state.started,
                progress: state.asProgress(),
                status: status(for: state.operation, completed: state.completed),
                definitionInfo: definitionInfo[state.definition]
            )
        }
        let recoverySummaries: [Summary] = recoveries.values.map { state in
            Summary(
                id: state.operation,
                type: .recovery,
                started: state.started,
                progress: state.asProgress(),
                status: status(for: state.operation, completed: state.completed),
                definitionInfo: nil
            )
        }
        operations = (backupSummaries + recoverySummaries).sorted(by: { $0.started > $1.started })
    }

    private func status(for operation: OperationId, completed: Date?) -> Summary.Status {
        if let completed { return .completed(completed) }
        if activeIds.contains(operation) { return .active }
        return .stopped
    }

    private func callback() -> OperationCallback {
        { [weak self] failure in
            guard let failure else { return }
            Task { @MainActor in self?.error = failure.localizedDescription }
        }
    }
}
