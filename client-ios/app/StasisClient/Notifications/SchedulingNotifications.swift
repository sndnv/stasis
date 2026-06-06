import Foundation
import OSLog
import StasisClientLib
import UserNotifications

public protocol SchedulingNotifications: Sendable {
    func requestAuthorization() async -> Bool
    func notifyPublicScheduleNotFound(activeSchedule: ActiveSchedule) async
    func notifyActiveScheduleNotFound(activeScheduleId: Int64) async
    func notifyOperationStarted(activeSchedule: ActiveSchedule) async
    func notifyOperationCompleted(activeSchedule: ActiveSchedule, failure: (any Error)?) async
}

public actor DefaultSchedulingNotifications: SchedulingNotifications {
    public static let categoryIdentifier: String = "stasis.client.ios.scheduling"

    private static let logger = Logger(subsystem: "stasis.client.ios", category: "SchedulingNotifications")

    private let center: UNUserNotificationCenter

    public init(center: UNUserNotificationCenter = .current()) {
        self.center = center
    }

    public func requestAuthorization() async -> Bool {
        do {
            return try await center.requestAuthorization(options: [.alert, .sound, .badge])
        } catch {
            Self.logger.error("notification authorization failed: \(error.localizedDescription)")
            return false
        }
    }

    public func notifyPublicScheduleNotFound(activeSchedule: ActiveSchedule) async {
        let operation = displayName(of: activeSchedule.assignment)
        await post(
            identifier: identifier(for: activeSchedule.id),
            title: "Missing schedule",
            body: "\(operation) operation is not associated with a valid schedule"
        )
    }

    public func notifyActiveScheduleNotFound(activeScheduleId: Int64) async {
        await post(
            identifier: identifier(for: activeScheduleId),
            title: "Invalid scheduled operation",
            body: "Attempted to execute a schedule with ID [\(activeScheduleId)] but it was not available"
        )
    }

    public func notifyOperationStarted(activeSchedule: ActiveSchedule) async {
        let operation = displayName(of: activeSchedule.assignment)
        await post(
            identifier: identifier(for: activeSchedule.id),
            title: "\(operation) started",
            body: "Running a new \(operation.lowercased()) operation"
        )
    }

    public func notifyOperationCompleted(activeSchedule: ActiveSchedule, failure: (any Error)?) async {
        let operation = displayName(of: activeSchedule.assignment)
        let title: String
        let body: String
        if let failure {
            title = "\(operation) operation failed"
            if let restricted = failure as? OperationRestrictedFailure {
                body = "Operation could not be started: \(restrictionsString(restricted.restrictions))"
            } else {
                body = failure.localizedDescription
            }
        } else {
            title = "\(operation) completed"
            body = "\(operation) operation completed successfully"
        }
        await post(
            identifier: identifier(for: activeSchedule.id),
            title: title,
            body: body
        )
    }

    private func restrictionsString(_ restrictions: [OperationRestriction]) -> String {
        restrictions.map { restriction in
            switch restriction {
            case .noConnection: "no network connection"
            case .limitedNetwork: "restricted or metered network"
            }
        }.joined(separator: ", ")
    }

    private func post(identifier: String, title: String, body: String) async {
        let content = UNMutableNotificationContent()
        content.title = title
        content.body = body
        content.categoryIdentifier = Self.categoryIdentifier
        let request = UNNotificationRequest(identifier: identifier, content: content, trigger: nil)
        do {
            try await center.add(request)
        } catch {
            Self.logger.error("failed to post notification [\(identifier)]: \(error.localizedDescription)")
        }
    }

    private func identifier(for activeScheduleId: Int64) -> String {
        "stasis.client.ios.scheduling.\(activeScheduleId)"
    }

    private func displayName(of assignment: OperationScheduleAssignment) -> String {
        switch assignment {
        case .backup: "Backup"
        case .expiration: "Expiration"
        case .validation: "Validation"
        case .keyRotation: "Key Rotation"
        }
    }
}
