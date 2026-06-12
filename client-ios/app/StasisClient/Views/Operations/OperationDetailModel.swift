import Foundation
import Observation
import StasisClientLib

struct OperationDetailKey: Hashable, Sendable {
    let id: OperationId
    let type: OperationType
}

@MainActor
@Observable
final class OperationDetailModel {
    let key: OperationDetailKey

    private let session: AuthenticatedSession
    private let trackers: DefaultTrackers

    private(set) var backup: BackupState?
    private(set) var recovery: RecoveryState?
    private(set) var definitionInfo: String?
    private(set) var isActive: Bool = false
    private(set) var isLoading: Bool = true
    private(set) var error: String?

    init(key: OperationDetailKey, session: AuthenticatedSession, trackers: DefaultTrackers) {
        self.key = key
        self.session = session
        self.trackers = trackers
    }

    func clearError() { error = nil }

    func refresh() async { await loadSnapshot() }

    func start() async {
        await loadSnapshot()
        await withTaskGroup(of: Void.self) { group in
            group.addTask { await self.observe() }
            group.addTask { await self.pollActive() }
        }
    }

    private func loadSnapshot() async {
        switch key.type {
        case .backup:
            let state = await trackers.backup.stateOf(operation: key.id)
            backup = state
            if let state {
                await resolveDefinitionInfo(state.definition)
            }
        case .recovery:
            recovery = await trackers.recovery.stateOf(operation: key.id)
        default:
            break
        }
        await refreshActive()
        isLoading = false
    }

    private func observe() async {
        switch key.type {
        case .backup:
            for await state in await trackers.backup.updates(operation: key.id) {
                backup = state
                if definitionInfo == nil {
                    await resolveDefinitionInfo(state.definition)
                }
            }
            backup = await trackers.backup.stateOf(operation: key.id)
        case .recovery:
            for await state in await trackers.recovery.updates(operation: key.id) {
                recovery = state
            }
            recovery = await trackers.recovery.stateOf(operation: key.id)
        default:
            break
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
        isActive = await session.operationExecutor.active().keys.contains(key.id)
    }

    private func resolveDefinitionInfo(_ id: DatasetDefinitionId) async {
        guard let definitions = try? await session.serverApiClient.datasetDefinitions() else { return }
        definitionInfo = definitions.first(where: { $0.id == id })?.info
    }
}
