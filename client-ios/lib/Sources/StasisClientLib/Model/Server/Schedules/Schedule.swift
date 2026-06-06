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

    public func nextInvocation(now: Date = Date(), calendar: Calendar = .current) -> Date? {
        guard let startDate = calendar.date(from: start.components) else { return nil }
        let intervalSeconds = max(interval.value, 1)
        if startDate >= now { return startDate }
        let elapsed = now.timeIntervalSince(startDate)
        let invocations = Int64(elapsed / TimeInterval(intervalSeconds))
        let offset = TimeInterval((invocations + 1) * intervalSeconds)
        return startDate.addingTimeInterval(offset)
    }

    public func lastInvocation(now: Date = Date(), calendar: Calendar = .current) -> Date? {
        guard let startDate = calendar.date(from: start.components) else { return nil }
        if startDate > now { return nil }
        let intervalSeconds = max(interval.value, 1)
        let elapsed = now.timeIntervalSince(startDate)
        let invocations = Int64(elapsed / TimeInterval(intervalSeconds))
        return startDate.addingTimeInterval(TimeInterval(invocations * intervalSeconds))
    }
}
