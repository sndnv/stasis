import Foundation

public struct ActiveSchedule: Sendable, Equatable, Hashable {
    public let id: Int64
    public let assignment: OperationScheduleAssignment

    public init(id: Int64, assignment: OperationScheduleAssignment) {
        self.id = id
        self.assignment = assignment
    }
}
