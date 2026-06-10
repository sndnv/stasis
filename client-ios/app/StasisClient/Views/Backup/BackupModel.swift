import Foundation
import Observation
import StasisClientLib

@MainActor
@Observable
final class BackupModel {
    private let session: AuthenticatedSession

    private(set) var definitions: [DatasetDefinition] = []
    private(set) var isLoading: Bool = true
    private(set) var error: String?

    init(session: AuthenticatedSession) {
        self.session = session
    }

    var defaultDefinitionId: DatasetDefinitionId? { definitions.first?.id }

    func clearError() { error = nil }

    func refresh() async { await load() }

    func load() async {
        isLoading = true
        do {
            let fetched = try await session.serverApiClient.datasetDefinitions()
            definitions = fetched.sorted(by: { $0.created < $1.created })
        } catch {
            self.error = error.localizedDescription
            definitions = []
        }
        isLoading = false
    }

    func createDefinition(_ request: CreateDatasetDefinition) async -> Bool {
        do {
            _ = try await session.serverApiClient.createDatasetDefinition(request: request)
            await load()
            return true
        } catch {
            self.error = error.localizedDescription
            return false
        }
    }

    func updateDefinition(_ id: DatasetDefinitionId, with request: UpdateDatasetDefinition) async -> Bool {
        do {
            try await session.serverApiClient.updateDatasetDefinition(definition: id, request: request)
            await load()
            return true
        } catch {
            self.error = error.localizedDescription
            return false
        }
    }

    func deleteDefinition(_ id: DatasetDefinitionId) async {
        do {
            try await session.serverApiClient.deleteDatasetDefinition(definition: id)
            await load()
        } catch {
            self.error = error.localizedDescription
        }
    }

    var selfDevice: DeviceId { session.serverApiClient.selfDevice }
}
