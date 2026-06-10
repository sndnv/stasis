import Foundation
import Observation

enum AppState: Equatable, Sendable {
    case unconfigured
    case configured
    case restoring
    case authenticated
}

@Observable
@MainActor
final class AppStateModel {
    private(set) var state: AppState

    init(configRepository: ConfigRepository, restorableSession: Bool = false) {
        let configAvailable = (try? configRepository.available()) ?? false
        switch (configAvailable, restorableSession) {
        case (false, _): self.state = .unconfigured
        case (true, false): self.state = .configured
        case (true, true): self.state = .restoring
        }
    }

    func transition(to newState: AppState) {
        state = newState
    }
}
