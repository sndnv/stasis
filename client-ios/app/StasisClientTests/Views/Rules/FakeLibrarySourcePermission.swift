import Foundation
@testable import StasisClient
import Synchronization

final class FakeLibrarySourcePermission: LibrarySourcePermission {
    private struct State {
        var status: LibraryPermissionStatus
        var grantOnRequest: Bool
        var requests: Int
    }

    private let state: Mutex<State>

    init(status: LibraryPermissionStatus, grantOnRequest: Bool) {
        self.state = Mutex(State(status: status, grantOnRequest: grantOnRequest, requests: 0))
    }

    func status() -> LibraryPermissionStatus {
        state.withLock { $0.status }
    }

    func request() async -> Bool {
        state.withLock { current in
            current.requests += 1
            current.status = current.grantOnRequest ? .granted : .denied
            return current.grantOnRequest
        }
    }

    var requestCount: Int {
        state.withLock { $0.requests }
    }
}
