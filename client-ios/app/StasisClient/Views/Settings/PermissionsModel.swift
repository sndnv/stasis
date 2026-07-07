import Contacts
import EventKit
import Foundation
import Observation
import Photos
import UIKit
import UserNotifications

@MainActor
@Observable
final class PermissionsModel {
    enum PermissionStatus: Equatable, Sendable {
        case granted, denied, provisional, unknown

        var label: String {
            switch self {
            case .granted: "Granted"
            case .denied: "Denied"
            case .provisional: "Provisional"
            case .unknown: "Unknown"
            }
        }
    }

    struct Item: Identifiable, Equatable, Sendable {
        let id: String
        let name: String
        let description: String
        let status: PermissionStatus
    }

    private(set) var items: [Item] = []
    private(set) var isLoading: Bool = true

    func refresh() async {
        isLoading = true
        items = [
            await notificationsItem(),
            backgroundRefreshItem(),
            photosItem(),
            contactsItem(),
            calendarItem()
        ]
        isLoading = false
    }

    private func calendarItem() -> Item {
        let status: PermissionStatus = switch EKEventStore.authorizationStatus(for: .event) {
        case .fullAccess: .granted
        case .writeOnly: .provisional
        case .denied, .restricted: .denied
        case .notDetermined: .unknown
        @unknown default: .unknown
        }
        return Item(
            id: "calendar",
            name: "Calendar",
            description: "Used to back up and restore your calendar events (experimental).",
            status: status
        )
    }

    private func contactsItem() -> Item {
        let status: PermissionStatus = switch CNContactStore.authorizationStatus(for: .contacts) {
        case .authorized: .granted
        case .limited: .provisional
        case .denied, .restricted: .denied
        case .notDetermined: .unknown
        @unknown default: .unknown
        }
        return Item(
            id: "contacts",
            name: "Contacts",
            description: "Used to back up and restore your contacts (experimental).",
            status: status
        )
    }

    private func photosItem() -> Item {
        let status: PermissionStatus = switch PHPhotoLibrary.authorizationStatus(for: .readWrite) {
        case .authorized: .granted
        case .limited: .provisional
        case .denied, .restricted: .denied
        case .notDetermined: .unknown
        @unknown default: .unknown
        }
        return Item(
            id: "photos",
            name: "Photos",
            description: "Used to back up and restore the photos and videos you choose.",
            status: status
        )
    }

    private func notificationsItem() async -> Item {
        let settings = await UNUserNotificationCenter.current().notificationSettings()
        let status: PermissionStatus = switch settings.authorizationStatus {
        case .authorized, .ephemeral: .granted
        case .denied: .denied
        case .provisional: .provisional
        case .notDetermined: .unknown
        @unknown default: .unknown
        }
        return Item(
            id: "notifications",
            name: "Notifications",
            description: "Used to notify you of completed and failed backup operations.",
            status: status
        )
    }

    private func backgroundRefreshItem() -> Item {
        let status: PermissionStatus = switch UIApplication.shared.backgroundRefreshStatus {
        case .available: .granted
        case .denied, .restricted: .denied
        @unknown default: .unknown
        }
        return Item(
            id: "backgroundRefresh",
            name: "Background App Refresh",
            description: "Allows scheduled backups to run while the app is in the background.",
            status: status
        )
    }
}
