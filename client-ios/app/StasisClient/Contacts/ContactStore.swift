import Foundation

protocol ContactStore: Sendable {
    func hasReadAccess() -> Bool

    func hasWriteAccess() -> Bool

    func list() async throws -> [StoredContact]

    func insert(_ record: ContactRecord) async throws

    func update(identifier: String, with record: ContactRecord) async throws
}
