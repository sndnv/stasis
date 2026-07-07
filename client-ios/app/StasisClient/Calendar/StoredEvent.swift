import Foundation

struct StoredEvent: Sendable, Equatable, Hashable {
    let identifier: String
    let record: CalendarEvent
}
