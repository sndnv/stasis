import Foundation

enum LibraryRecordKinds {
    static func all() -> [any LibraryRecordPreviewing] {
        [
            LibraryRecordKind(source: ContactsSource(store: DeviceContactStore())),
            LibraryRecordKind(source: CalendarSource(store: DeviceEventStore()))
        ]
    }

    static func byScheme() -> [String: any LibraryRecordPreviewing] {
        Dictionary(uniqueKeysWithValues: all().map { ($0.scheme, $0) })
    }
}
