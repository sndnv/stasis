import Foundation
@testable import StasisClient
import Synchronization

final class MockBackgroundTaskScheduler: BackgroundTaskScheduling, @unchecked Sendable {
    struct Submission: Sendable, Equatable {
        let identifier: String
        let earliestBeginDate: Date
        let requiresNetwork: Bool
    }

    private struct State {
        var submissions: [Submission] = []
        var cancellations: [String] = []
        var nextSubmitError: (any Error)?
    }

    private let state = Mutex(State())

    var submissions: [Submission] { state.withLock { $0.submissions } }
    var cancellations: [String] { state.withLock { $0.cancellations } }

    func setNextSubmitError(_ error: (any Error)?) {
        state.withLock { $0.nextSubmitError = error }
    }

    func submitProcessing(identifier: String, earliestBeginDate: Date, requiresNetwork: Bool) throws {
        try state.withLock {
            if let error = $0.nextSubmitError {
                $0.nextSubmitError = nil
                throw error
            }
            $0.submissions.append(Submission(
                identifier: identifier,
                earliestBeginDate: earliestBeginDate,
                requiresNetwork: requiresNetwork
            ))
        }
    }

    func cancel(identifier: String) {
        state.withLock { $0.cancellations.append(identifier) }
    }
}
