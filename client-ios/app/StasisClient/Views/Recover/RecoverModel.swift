import Foundation
import Observation
import StasisClientLib

@MainActor
@Observable
final class RecoverModel {
    private let session: AuthenticatedSession

    private(set) var definitions: [DatasetDefinition] = []
    private(set) var entries: [DatasetEntry] = []
    private(set) var isLoadingDefinitions: Bool = true
    private(set) var isLoadingEntries: Bool = false
    private(set) var startingRecovery: Bool = false
    var didStartRecovery: Bool = false
    private(set) var error: String?

    private var entriesLoadedFor: DatasetDefinitionId?

    init(session: AuthenticatedSession) {
        self.session = session
    }

    func clearError() { error = nil }

    func refresh() async { await load() }

    func load() async {
        isLoadingDefinitions = true
        do {
            let all = try await session.serverApiClient.datasetDefinitions()
            let api = session.serverApiClient
            let nonEmpty = await withTaskGroup(of: DatasetDefinition?.self) { group in
                for definition in all {
                    group.addTask {
                        let latest = try? await api.latestEntry(definition: definition.id, until: nil)
                        return latest == nil ? nil : definition
                    }
                }
                var collected: [DatasetDefinition] = []
                for await result in group {
                    if let result { collected.append(result) }
                }
                return collected
            }
            definitions = nonEmpty.sorted(by: { $0.info < $1.info })
        } catch {
            self.error = error.localizedDescription
            definitions = []
        }
        isLoadingDefinitions = false
    }

    func loadEntriesIfNeeded(for definition: DatasetDefinitionId) async {
        guard entriesLoadedFor != definition else { return }
        isLoadingEntries = true
        do {
            let fetched = try await session.serverApiClient.datasetEntries(definition: definition)
            entries = fetched.sorted(by: { $0.created > $1.created })
            entriesLoadedFor = definition
        } catch {
            self.error = error.localizedDescription
            entries = []
            entriesLoadedFor = nil
        }
        isLoadingEntries = false
    }

    func resetEntries() {
        entries = []
        entriesLoadedFor = nil
    }

    func startRecovery(config: RecoverConfig) async {
        guard !startingRecovery, case .valid = config.validate(), let definition = config.definition else {
            return
        }
        startingRecovery = true
        let active = await session.operationExecutor.active()
        guard active.isEmpty else {
            error = "Other operations are in progress."
            startingRecovery = false
            return
        }
        let callback: OperationCallback = { [weak self] failure in
            guard let failure else { return }
            Task { @MainActor in self?.error = failure.localizedDescription }
        }
        switch config.recoverySource {
        case .latest:
            _ = await session.operationExecutor.startRecoveryWithDefinition(
                definition: definition, until: nil,
                entities: nil, sources: config.sources,
                destination: nil,
                callback: callback
            )
        case .entry(let entry):
            guard let entry else { startingRecovery = false; return }
            _ = await session.operationExecutor.startRecoveryWithEntry(
                entry: entry,
                entities: nil, sources: config.sources,
                destination: nil,
                callback: callback
            )
        case .until(let date):
            _ = await session.operationExecutor.startRecoveryWithDefinition(
                definition: definition, until: date,
                entities: nil, sources: config.sources,
                destination: nil,
                callback: callback
            )
        }
        startingRecovery = false
        didStartRecovery = true
    }
}
