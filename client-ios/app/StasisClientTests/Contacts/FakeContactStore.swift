import Foundation
@testable import StasisClient
import Synchronization

final class FakeContactStore: ContactStore {
    private struct State {
        var stored: [StoredContact]
        var readAccess: Bool
        var writeAccess: Bool
        var nextId: Int
    }

    private let state: Mutex<State>

    init(stored: [StoredContact], readAccess: Bool, writeAccess: Bool) {
        self.state = Mutex(State(stored: stored, readAccess: readAccess, writeAccess: writeAccess, nextId: 0))
    }

    var contacts: [StoredContact] {
        state.withLock { $0.stored }
    }

    func hasReadAccess() -> Bool {
        state.withLock { $0.readAccess }
    }

    func hasWriteAccess() -> Bool {
        state.withLock { $0.writeAccess }
    }

    func list() async throws -> [StoredContact] {
        state.withLock { $0.stored }
    }

    func insert(_ record: ContactRecord) async throws {
        state.withLock { current in
            let identifier = "inserted-\(current.nextId)"
            current.nextId += 1
            current.stored.append(StoredContact(identifier: identifier, record: record))
        }
    }

    func update(identifier: String, with record: ContactRecord) async throws {
        state.withLock { current in
            if let index = current.stored.firstIndex(where: { $0.identifier == identifier }) {
                current.stored[index] = StoredContact(identifier: identifier, record: record)
            }
        }
    }
}
