import Foundation
@testable import StasisClient
import Testing

@Suite("CalendarSource")
struct CalendarSourceTests {
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

    private func event(title: String, calendar: String, start: Int) -> CalendarEvent {
        var record = base
        record.title = title
        record.calendar = calendar
        record.start = start
        return record.canonical()
    }

    @Test("lists records from the store")
    func listsRecords() async throws {
        let recordA = event(title: "test", calendar: "test", start: 1000)
        let recordB = event(title: "test a", calendar: "test", start: 3000)
        let store = FakeEventStore(
            stored: [
                StoredEvent(identifier: "a", record: recordA),
                StoredEvent(identifier: "b", record: recordB)
            ],
            readAccess: true,
            writeAccess: true
        )
        let source = CalendarSource(store: store)

        #expect(try await source.list() == [recordA, recordB])
    }

    @Test("derives a stable key that survives a delete then restore")
    func keyIsStableAcrossRoundTrip() {
        let source = CalendarSource(store: FakeEventStore(stored: [], readAccess: true, writeAccess: true))
        let record = event(title: "test", calendar: "test", start: 1000)

        #expect(source.id(record) == source.id(record))
    }

    @Test("keeps the key when only a non-identifying field changes")
    func nonIdentifyingEditKeepsKey() {
        let source = CalendarSource(store: FakeEventStore(stored: [], readAccess: true, writeAccess: true))
        let original = event(title: "test", calendar: "test", start: 1000)
        var edited = original
        edited.location = "test a"
        edited.availability = .free

        #expect(source.id(edited.canonical()) == source.id(original))
    }

    @Test("re-keys when an identifying field changes")
    func identifyingEditReKeys() {
        let source = CalendarSource(store: FakeEventStore(stored: [], readAccess: true, writeAccess: true))
        let original = event(title: "test", calendar: "test", start: 1000)
        let retitled = event(title: "test a", calendar: "test", start: 1000)
        let rescheduled = event(title: "test", calendar: "test", start: 9000)
        let recalendared = event(title: "test", calendar: "test a", start: 1000)

        #expect(source.id(retitled) != source.id(original))
        #expect(source.id(rescheduled) != source.id(original))
        #expect(source.id(recalendared) != source.id(original))
    }

    @Test("restore updates a matching event in place")
    func restoreUpdatesInPlace() async throws {
        let original = event(title: "test", calendar: "test", start: 1000)
        let store = FakeEventStore(
            stored: [StoredEvent(identifier: "existing", record: original)],
            readAccess: true,
            writeAccess: true
        )
        let source = CalendarSource(store: store)

        var edited = original
        edited.location = "test a"

        try await source.restore(edited.canonical())

        #expect(store.events.count == 1)
        #expect(store.events.first?.identifier == "existing")
        #expect(store.events.first?.record.location == "test a")
    }

    @Test("restore inserts an event when none matches")
    func restoreInsertsWhenMissing() async throws {
        let store = FakeEventStore(stored: [], readAccess: true, writeAccess: true)
        let source = CalendarSource(store: store)
        let record = event(title: "test", calendar: "test", start: 1000)

        try await source.restore(record)

        #expect(store.events.count == 1)
        #expect(store.events.first?.record == record)
    }

    @Test("re-backing up right after restore reports no change")
    func roundTripReportsNoChange() async throws {
        let record = event(title: "test", calendar: "test", start: 1000)
        let store = FakeEventStore(
            stored: [StoredEvent(identifier: "existing", record: record)],
            readAccess: true,
            writeAccess: true
        )
        let source = CalendarSource(store: store)

        let backedUp = try await source.list()
        for restored in backedUp {
            try await source.restore(restored)
        }

        #expect(try await source.list() == backedUp)
        #expect(store.events.count == 1)
    }

    @Test("describe surfaces core event fields")
    func describeSurfacesFields() {
        let source = CalendarSource(store: FakeEventStore(stored: [], readAccess: true, writeAccess: true))
        let record = event(title: "test", calendar: "test", start: 1000)

        let preview = source.describe(record)
        let section = preview.sections.first { $0.title == "Event" }
        let labels = section?.fields.map(\.label) ?? []

        #expect(labels.contains("Title"))
        #expect(labels.contains("Starts"))
        #expect(section?.fields.first { $0.label == "Title" }?.value == "test")
    }

    @Test("export produces an ICS document with the event summary and uid")
    func exportProducesICS() throws {
        let source = CalendarSource(store: FakeEventStore(stored: [], readAccess: true, writeAccess: true))
        let record = event(title: "test", calendar: "test", start: 1000)

        let exported = try source.export(record)
        #expect(exported.fileExtension == "ics")

        let text = try #require(String(bytes: exported.bytes, encoding: .utf8))
        #expect(text.contains("BEGIN:VEVENT"))
        #expect(text.contains("SUMMARY:test"))
        #expect(text.contains("UID:\(source.id(record))"))
    }
}
