import Foundation

protocol EventStore: Sendable {
    func hasReadAccess() -> Bool

    func hasWriteAccess() -> Bool

    func list() async throws -> [StoredEvent]

    func insert(_ record: CalendarEvent) async throws

    func update(identifier: String, with record: CalendarEvent) async throws
}
