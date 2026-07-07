import Foundation

struct CalendarEvent: Codable, Sendable, Equatable, Hashable {
    var calendar: String
    var title: String
    var notes: String?
    var location: String?
    var start: Int
    var end: Int
    var isAllDay: Bool
    var timeZone: String?
    var url: String?
    var availability: Availability
    var alarms: [Alarm]
    var recurrenceRules: [Recurrence]

    func canonical() -> CalendarEvent {
        var copy = self
        copy.alarms.sort { $0.sortKey < $1.sortKey }
        copy.recurrenceRules = copy.recurrenceRules.map { $0.canonical() }
        copy.recurrenceRules.sort { $0.sortKey < $1.sortKey }
        return copy
    }

    enum Availability: String, Codable, Sendable, Hashable {
        case busy
        case free
        case tentative
        case unavailable
        case notSupported
    }

    struct Alarm: Codable, Sendable, Equatable, Hashable {
        var relativeOffset: Int?
        var absoluteDate: Int?

        var sortKey: String { "\(relativeOffset ?? 0)\u{1F}\(absoluteDate ?? 0)" }
    }

    struct Recurrence: Codable, Sendable, Equatable, Hashable {
        var frequency: Frequency
        var interval: Int
        var daysOfWeek: [DayOfWeek]
        var daysOfMonth: [Int]
        var daysOfYear: [Int]
        var weeksOfYear: [Int]
        var monthsOfYear: [Int]
        var setPositions: [Int]
        var end: End?

        func canonical() -> Recurrence {
            var copy = self
            copy.daysOfWeek.sort { $0.sortKey < $1.sortKey }
            copy.daysOfMonth.sort()
            copy.daysOfYear.sort()
            copy.weeksOfYear.sort()
            copy.monthsOfYear.sort()
            copy.setPositions.sort()
            return copy
        }

        var sortKey: String {
            let parts: [String] = [
                frequency.rawValue,
                String(interval),
                daysOfWeek.map(\.sortKey).joined(separator: ","),
                Self.join(daysOfMonth),
                Self.join(daysOfYear),
                Self.join(weeksOfYear),
                Self.join(monthsOfYear),
                Self.join(setPositions),
                end?.sortKey ?? ""
            ]
            return parts.joined(separator: "\u{1F}")
        }

        private static func join(_ values: [Int]) -> String {
            values.map { String($0) }.joined(separator: ",")
        }

        enum Frequency: String, Codable, Sendable, Hashable {
            case daily
            case weekly
            case monthly
            case yearly
        }

        struct DayOfWeek: Codable, Sendable, Equatable, Hashable {
            var dayOfWeek: Int
            var weekNumber: Int

            var sortKey: String { "\(dayOfWeek)\u{1F}\(weekNumber)" }
        }

        enum End: Codable, Sendable, Equatable, Hashable {
            case occurrenceCount(Int)
            case endDate(Int)

            var sortKey: String {
                switch self {
                case .occurrenceCount(let count): "count\u{1F}\(count)"
                case .endDate(let date): "date\u{1F}\(date)"
                }
            }
        }
    }
}
