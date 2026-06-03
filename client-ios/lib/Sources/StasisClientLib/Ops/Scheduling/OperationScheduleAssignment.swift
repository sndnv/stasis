import Foundation

public enum OperationScheduleAssignment: Sendable, Equatable, Hashable {
    case backup(schedule: ScheduleId, definition: DatasetDefinitionId, entities: [URL])
    case expiration(schedule: ScheduleId)
    case validation(schedule: ScheduleId)
    case keyRotation(schedule: ScheduleId)

    public var schedule: ScheduleId {
        switch self {
        case .backup(let schedule, _, _): schedule
        case .expiration(let schedule): schedule
        case .validation(let schedule): schedule
        case .keyRotation(let schedule): schedule
        }
    }
}
