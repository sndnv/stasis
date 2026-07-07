import CryptoKit
import Foundation

struct CalendarSource: LibraryRecordSource {
    static let scheme = "calendar"

    private let store: any EventStore

    init(store: any EventStore) {
        self.store = store
    }

    var scheme: String { Self.scheme }

    func hasReadAccess() -> Bool {
        store.hasReadAccess()
    }

    func hasWriteAccess() -> Bool {
        store.hasWriteAccess()
    }

    func list() async throws -> [CalendarEvent] {
        try await store.list().map(\.record)
    }

    func restore(_ record: CalendarEvent) async throws {
        let key = id(record)
        if let match = try await store.list().first(where: { id($0.record) == key }) {
            try await store.update(identifier: match.identifier, with: record)
        } else {
            try await store.insert(record)
        }
    }

    func id(_ record: CalendarEvent) -> String {
        let identity = [record.title, String(record.start), record.calendar].joined(separator: "\n")
        return SHA256.hash(data: Data(identity.utf8)).map { String(format: "%02x", $0) }.joined()
    }

    func displayName(_ record: CalendarEvent) -> String {
        record.title
    }

    func attributes(_ record: CalendarEvent) -> [String: String] {
        ["name": record.title, "calendar": record.calendar]
    }

    func describe(_ record: CalendarEvent) -> EntityPreview {
        var fields: [EntityPreview.Field] = [
            EntityPreview.Field(label: "Title", value: record.title),
            EntityPreview.Field(label: "Calendar", value: record.calendar),
            EntityPreview.Field(label: "Starts", value: Self.format(record.start, allDay: record.isAllDay)),
            EntityPreview.Field(label: "Ends", value: Self.format(record.end, allDay: record.isAllDay)),
            EntityPreview.Field(label: "All Day", value: record.isAllDay ? "Yes" : "No"),
            EntityPreview.Field(label: "Availability", value: record.availability.rawValue)
        ]
        if let location = record.location, !location.isEmpty {
            fields.append(EntityPreview.Field(label: "Location", value: location))
        }
        if let notes = record.notes, !notes.isEmpty {
            fields.append(EntityPreview.Field(label: "Notes", value: notes))
        }
        if let url = record.url, !url.isEmpty {
            fields.append(EntityPreview.Field(label: "URL", value: url))
        }
        if let timeZone = record.timeZone, !timeZone.isEmpty {
            fields.append(EntityPreview.Field(label: "Time Zone", value: timeZone))
        }

        var sections = [EntityPreview.Section(title: "Event", fields: fields)]
        if !record.recurrenceRules.isEmpty {
            sections.append(EntityPreview.Section(
                title: "Recurrence",
                fields: record.recurrenceRules.map { EntityPreview.Field(label: "Rule", value: Self.summary($0)) }
            ))
        }
        if !record.alarms.isEmpty {
            sections.append(EntityPreview.Section(
                title: "Alarms",
                fields: record.alarms.map { EntityPreview.Field(label: "Alarm", value: Self.summary($0)) }
            ))
        }
        return EntityPreview(sections: sections)
    }

    func export(_ record: CalendarEvent) throws -> ExportedContent {
        ExportedContent(
            fileExtension: "ics",
            mimeType: "text/calendar",
            bytes: CalendarICal.data(from: record, uid: id(record), stamp: Date())
        )
    }

    private static func format(_ seconds: Int, allDay: Bool) -> String {
        let date = Date(timeIntervalSince1970: TimeInterval(seconds))
        return date.formatted(date: .abbreviated, time: allDay ? .omitted : .shortened)
    }

    private static func summary(_ recurrence: CalendarEvent.Recurrence) -> String {
        var parts = [recurrence.frequency.rawValue]
        if recurrence.interval > 1 { parts.append("every \(recurrence.interval)") }
        switch recurrence.end {
        case .occurrenceCount(let count): parts.append("for \(count)")
        case .endDate(let seconds): parts.append("until \(format(seconds, allDay: true))")
        case nil: break
        }
        return parts.joined(separator: ", ")
    }

    private static func summary(_ alarm: CalendarEvent.Alarm) -> String {
        if let absolute = alarm.absoluteDate {
            return format(absolute, allDay: false)
        }
        let offset = alarm.relativeOffset ?? 0
        let minutes = abs(offset) / 60
        return offset <= 0 ? "\(minutes) min before" : "\(minutes) min after"
    }
}
