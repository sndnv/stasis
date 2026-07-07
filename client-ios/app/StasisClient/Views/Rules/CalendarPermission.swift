import EventKit
import Foundation

struct CalendarPermission: LibrarySourcePermission {
    func status() -> LibraryPermissionStatus {
        Self.map(EKEventStore.authorizationStatus(for: .event))
    }

    func request() async -> Bool {
        (try? await EKEventStore().requestFullAccessToEvents()) ?? false
    }

    private static func map(_ status: EKAuthorizationStatus) -> LibraryPermissionStatus {
        switch status {
        case .fullAccess: .granted
        case .writeOnly, .denied, .restricted: .denied
        case .notDetermined: .undetermined
        @unknown default: .denied
        }
    }
}
