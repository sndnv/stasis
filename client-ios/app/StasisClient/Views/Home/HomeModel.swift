import Foundation
import Observation
import StasisClientLib

@MainActor
@Observable
final class HomeModel {
    struct LastOperation: Equatable, Sendable {
        let id: OperationId
        let type: OperationType
        let progress: OperationProgress
        let completed: Date
    }

    private let session: AuthenticatedSession
    private let trackers: DefaultTrackers
    private let ruleRepository: RuleRepository

    private(set) var firstDefinition: DatasetDefinition?
    private(set) var lastEntry: DatasetEntry?
    private(set) var lastOperation: LastOperation?
    private(set) var isLoading: Bool = true
    private(set) var startingBackup: Bool = false
    private(set) var error: String?

    private var latestBackups: [OperationId: BackupState] = [:]
    private var latestRecoveries: [OperationId: RecoveryState] = [:]

    init(session: AuthenticatedSession, trackers: DefaultTrackers, ruleRepository: RuleRepository) {
        self.session = session
        self.trackers = trackers
        self.ruleRepository = ruleRepository
    }

    func clearError() {
        error = nil
    }

    func refresh() async {
        await load()
    }

    func start() async {
        await load()
        await withTaskGroup(of: Void.self) { group in
            group.addTask { await self.observeBackups() }
            group.addTask { await self.observeRecoveries() }
        }
    }

    func startBackup() async {
        guard let definition = firstDefinition, !startingBackup else { return }
        startingBackup = true
        error = nil
        let rules: [Rule]
        do {
            rules = try await ruleRepository.rules()
        } catch {
            self.error = error.localizedDescription
            startingBackup = false
            return
        }
        _ = await session.operationExecutor.startBackupWithRules(
            definition: definition.id,
            rules: rules,
            callback: makeBackupCallback()
        )
        startingBackup = false
    }

    func load() async {
        do {
            let definitions = try await session.serverApiClient.datasetDefinitions()
            firstDefinition = definitions.min(by: { $0.created < $1.created })
            if let definition = firstDefinition {
                lastEntry = try await session.serverApiClient.latestEntry(
                    definition: definition.id, until: nil
                )
            }
        } catch {
            self.error = error.localizedDescription
        }
        latestBackups = await trackers.backup.snapshot()
        latestRecoveries = await trackers.recovery.snapshot()
        recomputeLastOperation()
        isLoading = false
    }

    private func observeBackups() async {
        for await update in await trackers.backup.updates() {
            latestBackups = update
            recomputeLastOperation()
        }
    }

    private func observeRecoveries() async {
        for await update in await trackers.recovery.updates() {
            latestRecoveries = update
            recomputeLastOperation()
        }
    }

    private func recomputeLastOperation() {
        let completedBackups = latestBackups.values.compactMap { state -> LastOperation? in
            guard let completed = state.completed else { return nil }
            return LastOperation(
                id: state.operation, type: .backup,
                progress: state.asProgress(), completed: completed
            )
        }
        let completedRecoveries = latestRecoveries.values.compactMap { state -> LastOperation? in
            guard let completed = state.completed else { return nil }
            return LastOperation(
                id: state.operation, type: .recovery,
                progress: state.asProgress(), completed: completed
            )
        }
        lastOperation = (completedBackups + completedRecoveries).max(by: { $0.completed < $1.completed })
    }

    private func makeBackupCallback() -> OperationCallback {
        { [weak self] failure in
            guard let failure else { return }
            Task { @MainActor in
                self?.error = failure.localizedDescription
            }
        }
    }
}
