import Foundation
import Observation
import StasisClientLib

@MainActor
@Observable
final class DefinitionDetailModel {
    private let session: AuthenticatedSession
    private let ruleRepository: RuleRepository
    let definition: DatasetDefinition

    private(set) var entries: [DatasetEntry] = []
    private(set) var isLoadingEntries: Bool = true
    private(set) var startingBackup: Bool = false
    var didStartBackup: Bool = false
    private(set) var error: String?

    init(session: AuthenticatedSession, ruleRepository: RuleRepository, definition: DatasetDefinition) {
        self.session = session
        self.ruleRepository = ruleRepository
        self.definition = definition
    }

    func clearError() { error = nil }

    func refresh() async { await loadEntries() }

    func load() async { await loadEntries() }

    func startBackup() async {
        guard !startingBackup else { return }
        startingBackup = true
        error = nil
        let active = await session.operationExecutor.active()
        guard active.isEmpty else {
            error = "Other operations are in progress."
            startingBackup = false
            return
        }
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
        didStartBackup = true
    }

    func deleteEntry(_ id: DatasetEntryId) async {
        do {
            try await session.serverApiClient.deleteDatasetEntry(entry: id)
            await loadEntries()
        } catch {
            self.error = error.localizedDescription
        }
    }

    private func loadEntries() async {
        isLoadingEntries = true
        do {
            let fetched = try await session.serverApiClient.datasetEntries(definition: definition.id)
            entries = fetched.sorted(by: { $0.created > $1.created })
        } catch {
            self.error = error.localizedDescription
            entries = []
        }
        isLoadingEntries = false
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
