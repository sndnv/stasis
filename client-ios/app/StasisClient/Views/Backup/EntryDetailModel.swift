import Foundation
import Observation
import StasisClientLib

@MainActor
@Observable
final class EntryDetailModel {
    private let session: AuthenticatedSession
    let entry: DatasetEntry

    private(set) var metadata: DatasetMetadata?
    private(set) var isLoading: Bool = true
    private(set) var error: String?

    init(session: AuthenticatedSession, entry: DatasetEntry) {
        self.session = session
        self.entry = entry
    }

    func clearError() { error = nil }

    func refresh() async { await load() }

    func load() async {
        isLoading = true
        do {
            metadata = try await session.serverApiClient.datasetMetadata(entry: entry)
        } catch {
            self.error = error.localizedDescription
            metadata = nil
        }
        isLoading = false
    }
}
