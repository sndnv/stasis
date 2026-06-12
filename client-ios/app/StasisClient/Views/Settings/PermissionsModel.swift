import Foundation
import Observation
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
            backgroundRefreshItem()
        ]
        isLoading = false
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
