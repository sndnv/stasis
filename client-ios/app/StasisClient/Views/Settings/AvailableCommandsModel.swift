import Foundation
import Observation
import StasisClientLib

@MainActor
@Observable
final class AvailableCommandsModel {
    enum LoadState: Equatable {
        case loading
        case loaded([CommandAsJson], lastProcessedCommand: Int64)
        case failed(String)
    }

    private let session: AuthenticatedSession
    private let preferences: UserDefaults

    private(set) var state: LoadState = .loading

    init(session: AuthenticatedSession, preferences: UserDefaults) {
        self.session = session
        self.preferences = preferences
    }

    func load() async {
        state = .loading
        do {
            let commands = try await session.serverApiClient.commands(lastSequenceId: nil)
            let sorted = commands.sorted(by: { $0.sequenceId > $1.sequenceId })
            state = .loaded(sorted, lastProcessedCommand: preferences.savedLastProcessedCommand())
        } catch {
            state = .failed(error.localizedDescription)
        }
    }
}
