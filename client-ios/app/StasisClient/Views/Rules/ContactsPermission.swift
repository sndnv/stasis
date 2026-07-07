import Contacts
import Foundation

struct ContactsPermission: LibrarySourcePermission {
    func status() -> LibraryPermissionStatus {
        Self.map(CNContactStore.authorizationStatus(for: .contacts))
    }

    func request() async -> Bool {
        (try? await CNContactStore().requestAccess(for: .contacts)) ?? false
    }

    private static func map(_ status: CNAuthorizationStatus) -> LibraryPermissionStatus {
        switch status {
        case .authorized, .limited: .granted
        case .denied, .restricted: .denied
        case .notDetermined: .undetermined
        @unknown default: .denied
        }
    }
}
