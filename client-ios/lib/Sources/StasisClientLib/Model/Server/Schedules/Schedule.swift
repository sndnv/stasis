import Foundation

public typealias ScheduleId = UUID

public struct Schedule: Sendable, Equatable, Hashable, Codable {
    public let id: ScheduleId
    public let info: String
    public let isPublic: Bool
    public let start: LocalDateTime
    public let interval: SecondsDuration
    public let created: Date
    public let updated: Date

    public init(
        id: ScheduleId,
        info: String,
        isPublic: Bool,
        start: LocalDateTime,
        interval: SecondsDuration,
        created: Date,
        updated: Date
    ) {
        self.id = id
        self.info = info
        self.isPublic = isPublic
        self.start = start
        self.interval = interval
        self.created = created
        self.updated = updated
    }
}
