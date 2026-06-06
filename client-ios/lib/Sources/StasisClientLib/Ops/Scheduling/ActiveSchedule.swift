import Foundation

public struct ActiveSchedule: Sendable, Equatable, Hashable {
    public let id: Int64
    public let assignment: OperationScheduleAssignment
    public let lastFiredAt: Date?

    public init(id: Int64, assignment: OperationScheduleAssignment, lastFiredAt: Date? = nil) {
        self.id = id
        self.assignment = assignment
        self.lastFiredAt = lastFiredAt
    }
}
