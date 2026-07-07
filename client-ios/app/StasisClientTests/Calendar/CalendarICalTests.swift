import Foundation
@testable import StasisClient
import Testing

@Suite("CalendarICal")
struct CalendarICalTests {
    private let stamp = Date(timeIntervalSince1970: 0)

    private var base: CalendarEvent {
        CalendarEvent(
            calendar: "test",
            title: "test",
            notes: nil,
            location: nil,
            start: 0,
            end: 3600,
            isAllDay: false,
            timeZone: "UTC",
            url: nil,
            availability: .busy,
            alarms: [],
            recurrenceRules: []
        )
    }

    private func lines(_ event: CalendarEvent) -> [String] {
        CalendarICal.text(from: event, uid: "test-uid", stamp: stamp)
            .components(separatedBy: "\r\n")
    }

    @Test("emits a timed event as UTC date-times")
    func emitsTimedEvent() {
        let output = lines(base)
        #expect(output.contains("BEGIN:VCALENDAR"))
        #expect(output.contains("UID:test-uid"))
        #expect(output.contains("DTSTART:19700101T000000Z"))
        #expect(output.contains("DTEND:19700101T010000Z"))
        #expect(output.contains("SUMMARY:test"))
        #expect(output.contains("END:VEVENT"))
    }

    @Test("emits an all-day event as floating dates with an exclusive end")
    func emitsAllDayEvent() {
        var event = base
        event.isAllDay = true
        event.start = 0
        event.end = 0

        let output = lines(event)
        #expect(output.contains("DTSTART;VALUE=DATE:19700101"))
        #expect(output.contains("DTEND;VALUE=DATE:19700102"))
    }

    @Test("builds a weekly RRULE with interval, day and count")
    func buildsWeeklyRRule() {
        var event = base
        event.recurrenceRules = [
            CalendarEvent.Recurrence(
                frequency: .weekly,
                interval: 2,
                daysOfWeek: [CalendarEvent.Recurrence.DayOfWeek(dayOfWeek: 2, weekNumber: 0)],
                daysOfMonth: [],
                daysOfYear: [],
                weeksOfYear: [],
                monthsOfYear: [],
                setPositions: [],
                end: .occurrenceCount(10)
            )
        ]

        #expect(lines(event).contains("RRULE:FREQ=WEEKLY;INTERVAL=2;BYDAY=MO;COUNT=10"))
    }

    @Test("builds a monthly RRULE with an ordinal weekday and set position")
    func buildsMonthlyOrdinalRRule() {
        var event = base
        event.recurrenceRules = [
            CalendarEvent.Recurrence(
                frequency: .monthly,
                interval: 1,
                daysOfWeek: [CalendarEvent.Recurrence.DayOfWeek(dayOfWeek: 6, weekNumber: -1)],
                daysOfMonth: [],
                daysOfYear: [],
                weeksOfYear: [],
                monthsOfYear: [],
                setPositions: [1],
                end: nil
            )
        ]

        #expect(lines(event).contains("RRULE:FREQ=MONTHLY;BYDAY=-1FR;BYSETPOS=1"))
    }

    @Test("emits a relative alarm as a duration trigger")
    func emitsRelativeAlarm() {
        var event = base
        event.alarms = [CalendarEvent.Alarm(relativeOffset: -600, absoluteDate: nil)]

        let output = lines(event)
        #expect(output.contains("BEGIN:VALARM"))
        #expect(output.contains("TRIGGER:-PT10M"))
    }

    @Test("emits an absolute alarm as a date-time trigger")
    func emitsAbsoluteAlarm() {
        var event = base
        event.alarms = [CalendarEvent.Alarm(relativeOffset: nil, absoluteDate: 3600)]

        #expect(lines(event).contains("TRIGGER;VALUE=DATE-TIME:19700101T010000Z"))
    }

    @Test("escapes commas and semicolons in text values")
    func escapesText() {
        var event = base
        event.title = "test, a; b"

        #expect(lines(event).contains("SUMMARY:test\\, a\\; b"))
    }

    @Test("folds physical lines to at most 75 octets")
    func foldsLongLines() {
        var event = base
        event.notes = String(repeating: "a", count: 300)

        for line in lines(event) {
            #expect(line.utf8.count <= 75)
        }
    }
}
