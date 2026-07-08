import Foundation
import StasisClientLib

public enum ConverterError: Error, Equatable, LocalizedError {
    case unexpectedAssignmentType(String)
    case malformedAssignmentData(String)

    public var errorDescription: String? {
        switch self {
        case .unexpectedAssignmentType(let value):
            "Unexpected assignment type [\(value)]"
        case .malformedAssignmentData(let reason):
            "Malformed assignment data: \(reason)"
        }
    }
}

public extension Rule {
    func toEntity() -> RuleEntity {
        RuleEntity(
            id: id,
            operation: operation,
            source: source,
            pattern: pattern,
            definition: definition
        )
    }
}

public extension RuleEntity {
    func toRule() throws -> Rule {
        Rule(
            id: id,
            operation: try RuleEntity.decode(operationRaw),
            source: source,
            pattern: pattern,
            definition: definition
        )
    }
}

public extension Schedule {
    func toLocalScheduleEntity() -> LocalScheduleEntity {
        LocalScheduleEntity(
            id: id,
            info: info,
            start: start.value,
            intervalSeconds: interval.value,
            created: created
        )
    }
}

public extension LocalScheduleEntity {
    func toSchedule() -> Schedule {
        Schedule(
            id: id,
            info: info,
            isPublic: false,
            start: LocalDateTime(start),
            interval: SecondsDuration(intervalSeconds),
            created: created,
            updated: created
        )
    }
}

public extension ActiveSchedule {
    func toEntity() throws -> ActiveScheduleEntity {
        let (type, data) = try ActiveScheduleConverter.encode(assignment: assignment)
        return ActiveScheduleEntity(
            id: 0,
            schedule: assignment.schedule,
            type: type,
            data: data,
            lastFiredAt: lastFiredAt
        )
    }
}

public extension ActiveScheduleEntity {
    func toActiveSchedule() throws -> ActiveSchedule {
        let assignment = try ActiveScheduleConverter.decode(schedule: schedule, type: type, data: data)
        return ActiveSchedule(id: id, assignment: assignment, lastFiredAt: lastFiredAt)
    }
}

enum ActiveScheduleConverter {
    private struct BackupPayload: Codable {
        let definition: String
        let entities: [String]
    }

    static func encode(assignment: OperationScheduleAssignment) throws -> (String, String?) {
        switch assignment {
        case .backup(_, let definition, let entities):
            let payload = BackupPayload(
                definition: definition.uuidString,
                entities: entities.map(\.path)
            )
            let json = try JSONEncoder().encode(payload)
            guard let raw = String(data: json, encoding: .utf8) else {
                throw ConverterError.malformedAssignmentData("backup payload not utf-8")
            }
            return ("backup", raw)
        case .expiration:
            return ("expiration", nil)
        case .validation:
            return ("validation", nil)
        case .keyRotation:
            return ("key_rotation", nil)
        }
    }

    static func decode(
        schedule: ScheduleId,
        type: String,
        data: String?
    ) throws -> OperationScheduleAssignment {
        switch type {
        case "backup":
            guard let raw = data?.data(using: .utf8) else {
                throw ConverterError.malformedAssignmentData("missing backup payload")
            }
            let payload = try JSONDecoder().decode(BackupPayload.self, from: raw)
            guard let definition = UUID(uuidString: payload.definition) else {
                throw ConverterError.malformedAssignmentData("invalid definition uuid")
            }
            return .backup(
                schedule: schedule,
                definition: definition,
                entities: payload.entities.map { URL(fileURLWithPath: $0) }
            )
        case "expiration":
            return .expiration(schedule: schedule)
        case "validation":
            return .validation(schedule: schedule)
        case "key_rotation":
            return .keyRotation(schedule: schedule)
        default:
            throw ConverterError.unexpectedAssignmentType(type)
        }
    }
}
