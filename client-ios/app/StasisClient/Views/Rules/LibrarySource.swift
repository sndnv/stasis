import Foundation

struct LibrarySource: Identifiable, Sendable {
    let scheme: String
    let displayName: String
    let systemImage: String
    let isExperimental: Bool
    let permission: any LibrarySourcePermission

    var id: String { scheme }

    static let photos = LibrarySource(
        scheme: PhotoAlbumSelector.scheme,
        displayName: "Photos",
        systemImage: "photo.on.rectangle",
        isExperimental: false,
        permission: PhotoLibraryPermission()
    )

    static let contacts = LibrarySource(
        scheme: ContactsSource.scheme,
        displayName: "Contacts",
        systemImage: "person.crop.circle",
        isExperimental: true,
        permission: ContactsPermission()
    )

    static let calendar = LibrarySource(
        scheme: CalendarSource.scheme,
        displayName: "Calendar",
        systemImage: "calendar",
        isExperimental: true,
        permission: CalendarPermission()
    )

    static let all: [LibrarySource] = [.photos, .contacts, .calendar]
}
