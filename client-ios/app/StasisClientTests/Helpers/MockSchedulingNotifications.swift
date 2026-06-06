import Foundation
@testable import StasisClient
import StasisClientLib

final actor MockSchedulingNotifications: SchedulingNotifications {
    struct Completion: Sendable {
        let activeScheduleId: Int64
        let failure: (any Error)?
    }

    private(set) var authorizationRequests: Int = 0
    private(set) var publicScheduleNotFound: [Int64] = []
    private(set) var activeScheduleNotFound: [Int64] = []
    private(set) var operationStarted: [Int64] = []
    private(set) var operationCompleted: [Completion] = []
    var authorizationOutcome: Bool = true

    func requestAuthorization() async -> Bool {
        authorizationRequests += 1
        return authorizationOutcome
    }

    func notifyPublicScheduleNotFound(activeSchedule: ActiveSchedule) async {
        publicScheduleNotFound.append(activeSchedule.id)
    }

    func notifyActiveScheduleNotFound(activeScheduleId: Int64) async {
        activeScheduleNotFound.append(activeScheduleId)
    }

    func notifyOperationStarted(activeSchedule: ActiveSchedule) async {
        operationStarted.append(activeSchedule.id)
    }

    func notifyOperationCompleted(activeSchedule: ActiveSchedule, failure: (any Error)?) async {
        operationCompleted.append(Completion(activeScheduleId: activeSchedule.id, failure: failure))
    }
}
