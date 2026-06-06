import StasisClientLib

public struct Schedules: Sendable, Equatable {
    public let publicSchedules: [Schedule]
    public let local: [Schedule]
    public let configured: [ActiveSchedule]

    public init(
        publicSchedules: [Schedule] = [],
        local: [Schedule] = [],
        configured: [ActiveSchedule] = []
    ) {
        self.publicSchedules = publicSchedules
        self.local = local
        self.configured = configured
    }

    public static let empty = Schedules()
}
