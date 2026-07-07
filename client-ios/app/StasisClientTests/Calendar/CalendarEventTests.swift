import Foundation
@testable import StasisClient
import Testing

@Suite("CalendarEvent")
struct CalendarEventTests {
    private var base: CalendarEvent {
        CalendarEvent(
            calendar: "test",
            title: "test",
            notes: nil,
            location: nil,
            start: 1000,
            end: 2000,
            isAllDay: false,
            timeZone: nil,
            url: nil,
            availability: .busy,
            alarms: [],
            recurrenceRules: []
        )
    }

    private func sortedEncode(_ record: CalendarEvent) throws -> Data {
        let encoder = JSONEncoder()
        encoder.outputFormatting = .sortedKeys
        return try encoder.encode(record)
    }

    @Test("round-trips through Codable")
    func codableRoundTrip() throws {
        var record = base
        record.notes = "test a"
        record.location = "test b"
        record.timeZone = "Europe/London"
        record.url = "https://example.com/test"
        record.availability = .tentative
        record.alarms = [
            CalendarEvent.Alarm(relativeOffset: -600, absoluteDate: nil),
            CalendarEvent.Alarm(relativeOffset: nil, absoluteDate: 5000)
        ]
        record.recurrenceRules = [
            CalendarEvent.Recurrence(
                frequency: .weekly,
                interval: 2,
                daysOfWeek: [CalendarEvent.Recurrence.DayOfWeek(dayOfWeek: 2, weekNumber: 0)],
                daysOfMonth: [1, 15],
                daysOfYear: [],
                weeksOfYear: [],
                monthsOfYear: [],
                setPositions: [],
                end: .occurrenceCount(10)
            )
        ]

        let decoded = try JSONDecoder().decode(CalendarEvent.self, from: try sortedEncode(record))
        #expect(decoded == record)
    }

    @Test("canonical sorts multi-value fields regardless of input order")
    func canonicalSortsFields() {
        var forward = base
        forward.alarms = [
            CalendarEvent.Alarm(relativeOffset: -300, absoluteDate: nil),
            CalendarEvent.Alarm(relativeOffset: -900, absoluteDate: nil)
        ]
        forward.recurrenceRules = [
            CalendarEvent.Recurrence(
                frequency: .monthly,
                interval: 1,
                daysOfWeek: [],
                daysOfMonth: [15, 1, 8],
                daysOfYear: [],
                weeksOfYear: [],
                monthsOfYear: [],
                setPositions: [],
                end: nil
            )
        ]

        var reversed = base
        reversed.alarms = Array(forward.alarms.reversed())
        reversed.recurrenceRules = [
            CalendarEvent.Recurrence(
                frequency: .monthly,
                interval: 1,
                daysOfWeek: [],
                daysOfMonth: [8, 1, 15],
                daysOfYear: [],
                weeksOfYear: [],
                monthsOfYear: [],
                setPositions: [],
                end: nil
            )
        ]

        #expect(forward.canonical() == reversed.canonical())
    }

    @Test("canonical events with equal data encode to identical bytes")
    func canonicalEncodingIsDeterministic() throws {
        var forward = base
        forward.title = "test"
        forward.alarms = [
            CalendarEvent.Alarm(relativeOffset: -900, absoluteDate: nil),
            CalendarEvent.Alarm(relativeOffset: -300, absoluteDate: nil)
        ]

        var reversed = base
        reversed.title = "test"
        reversed.alarms = Array(forward.alarms.reversed())

        #expect(try sortedEncode(forward.canonical()) == (try sortedEncode(reversed.canonical())))
    }
}
