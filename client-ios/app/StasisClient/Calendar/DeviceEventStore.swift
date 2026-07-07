import EventKit
import Foundation
import StasisClientLib

struct DeviceEventStore: EventStore {
    private static let lookBackYears = 4
    private static let lookAheadYears = 1

    func hasReadAccess() -> Bool {
        EKEventStore.authorizationStatus(for: .event) == .fullAccess
    }

    func hasWriteAccess() -> Bool {
        hasReadAccess()
    }

    func list() async throws -> [StoredEvent] {
        let store = EKEventStore()
        let now = Date()
        let calendar = Calendar.current
        let start = calendar.date(byAdding: .year, value: -Self.lookBackYears, to: now) ?? now
        let end = calendar.date(byAdding: .year, value: Self.lookAheadYears, to: now) ?? now
        let predicate = store.predicateForEvents(withStart: start, end: end, calendars: nil)

        var seen: Set<String> = []
        var results: [StoredEvent] = []
        for event in store.events(matching: predicate) {
            guard let identifier = event.eventIdentifier, seen.insert(identifier).inserted else { continue }
            results.append(StoredEvent(identifier: identifier, record: Self.record(from: event)))
        }
        return results
    }

    func insert(_ record: CalendarEvent) async throws {
        let store = EKEventStore()
        guard let calendar = Self.calendar(named: record.calendar, in: store) else {
            throw InvalidArgumentError("No writable calendar available to restore event [\(record.title)]")
        }
        let event = EKEvent(eventStore: store)
        event.calendar = calendar
        Self.apply(record, to: event)
        try store.save(event, span: Self.span(for: record), commit: true)
    }

    func update(identifier: String, with record: CalendarEvent) async throws {
        let store = EKEventStore()
        guard let event = store.event(withIdentifier: identifier) else {
            throw InvalidArgumentError("Unable to update event [\(identifier)]")
        }
        Self.apply(record, to: event)
        try store.save(event, span: Self.span(for: record), commit: true)
    }

    private static func span(for record: CalendarEvent) -> EKSpan {
        record.recurrenceRules.isEmpty ? .thisEvent : .futureEvents
    }

    private static func calendar(named title: String, in store: EKEventStore) -> EKCalendar? {
        let writable = store.calendars(for: .event).filter { $0.allowsContentModifications }
        return writable.first { $0.title == title } ?? store.defaultCalendarForNewEvents ?? writable.first
    }

    private static func record(from event: EKEvent) -> CalendarEvent {
        CalendarEvent(
            calendar: event.calendar?.title ?? "",
            title: event.title ?? "",
            notes: event.notes,
            location: event.location,
            start: seconds(from: event.startDate),
            end: seconds(from: event.endDate),
            isAllDay: event.isAllDay,
            timeZone: event.timeZone?.identifier,
            url: event.url?.absoluteString,
            availability: availability(from: event.availability),
            alarms: (event.alarms ?? []).map { alarm(from: $0) },
            recurrenceRules: (event.recurrenceRules ?? []).map { recurrence(from: $0) }
        ).canonical()
    }

    private static func apply(_ record: CalendarEvent, to event: EKEvent) {
        event.title = record.title
        event.notes = record.notes
        event.location = record.location
        event.startDate = date(from: record.start)
        event.endDate = date(from: record.end)
        event.isAllDay = record.isAllDay
        event.timeZone = record.timeZone.flatMap { TimeZone(identifier: $0) }
        event.url = record.url.flatMap { URL(string: $0) }
        event.availability = availability(from: record.availability)
        event.alarms = record.alarms.map { alarm(from: $0) }
        event.recurrenceRules = record.recurrenceRules.map { recurrenceRule(from: $0) }
    }

    private static func seconds(from date: Date?) -> Int {
        guard let date else { return 0 }
        return Int(date.timeIntervalSince1970)
    }

    private static func date(from seconds: Int) -> Date {
        Date(timeIntervalSince1970: TimeInterval(seconds))
    }

    private static func availability(from value: EKEventAvailability) -> CalendarEvent.Availability {
        switch value {
        case .busy: .busy
        case .free: .free
        case .tentative: .tentative
        case .unavailable: .unavailable
        case .notSupported: .notSupported
        @unknown default: .busy
        }
    }

    private static func availability(from value: CalendarEvent.Availability) -> EKEventAvailability {
        switch value {
        case .busy: .busy
        case .free: .free
        case .tentative: .tentative
        case .unavailable: .unavailable
        case .notSupported: .notSupported
        }
    }

    private static func alarm(from alarm: EKAlarm) -> CalendarEvent.Alarm {
        if let absolute = alarm.absoluteDate {
            CalendarEvent.Alarm(relativeOffset: nil, absoluteDate: seconds(from: absolute))
        } else {
            CalendarEvent.Alarm(relativeOffset: Int(alarm.relativeOffset), absoluteDate: nil)
        }
    }

    private static func alarm(from alarm: CalendarEvent.Alarm) -> EKAlarm {
        if let absolute = alarm.absoluteDate {
            EKAlarm(absoluteDate: date(from: absolute))
        } else {
            EKAlarm(relativeOffset: TimeInterval(alarm.relativeOffset ?? 0))
        }
    }

    private static func recurrence(from rule: EKRecurrenceRule) -> CalendarEvent.Recurrence {
        CalendarEvent.Recurrence(
            frequency: frequency(from: rule.frequency),
            interval: rule.interval,
            daysOfWeek: (rule.daysOfTheWeek ?? []).map {
                CalendarEvent.Recurrence.DayOfWeek(dayOfWeek: $0.dayOfTheWeek.rawValue, weekNumber: $0.weekNumber)
            },
            daysOfMonth: (rule.daysOfTheMonth ?? []).map(\.intValue),
            daysOfYear: (rule.daysOfTheYear ?? []).map(\.intValue),
            weeksOfYear: (rule.weeksOfTheYear ?? []).map(\.intValue),
            monthsOfYear: (rule.monthsOfTheYear ?? []).map(\.intValue),
            setPositions: (rule.setPositions ?? []).map(\.intValue),
            end: end(from: rule.recurrenceEnd)
        )
    }

    private static func recurrenceRule(from recurrence: CalendarEvent.Recurrence) -> EKRecurrenceRule {
        EKRecurrenceRule(
            recurrenceWith: frequency(from: recurrence.frequency),
            interval: max(recurrence.interval, 1),
            daysOfTheWeek: recurrence.daysOfWeek.isEmpty ? nil : recurrence.daysOfWeek.map {
                EKRecurrenceDayOfWeek(dayOfTheWeek: EKWeekday(rawValue: $0.dayOfWeek) ?? .sunday, weekNumber: $0.weekNumber)
            },
            daysOfTheMonth: numbers(recurrence.daysOfMonth),
            monthsOfTheYear: numbers(recurrence.monthsOfYear),
            weeksOfTheYear: numbers(recurrence.weeksOfYear),
            daysOfTheYear: numbers(recurrence.daysOfYear),
            setPositions: numbers(recurrence.setPositions),
            end: end(from: recurrence.end)
        )
    }

    private static func numbers(_ values: [Int]) -> [NSNumber]? {
        values.isEmpty ? nil : values.map { NSNumber(value: $0) }
    }

    private static func frequency(from value: EKRecurrenceFrequency) -> CalendarEvent.Recurrence.Frequency {
        switch value {
        case .daily: .daily
        case .weekly: .weekly
        case .monthly: .monthly
        case .yearly: .yearly
        @unknown default: .daily
        }
    }

    private static func frequency(from value: CalendarEvent.Recurrence.Frequency) -> EKRecurrenceFrequency {
        switch value {
        case .daily: .daily
        case .weekly: .weekly
        case .monthly: .monthly
        case .yearly: .yearly
        }
    }

    private static func end(from end: EKRecurrenceEnd?) -> CalendarEvent.Recurrence.End? {
        guard let end else { return nil }
        if end.occurrenceCount > 0 {
            return .occurrenceCount(end.occurrenceCount)
        }
        if let date = end.endDate {
            return .endDate(seconds(from: date))
        }
        return nil
    }

    private static func end(from end: CalendarEvent.Recurrence.End?) -> EKRecurrenceEnd? {
        switch end {
        case .occurrenceCount(let count): EKRecurrenceEnd(occurrenceCount: count)
        case .endDate(let seconds): EKRecurrenceEnd(end: date(from: seconds))
        case nil: nil
        }
    }
}
