import Foundation
import SwiftData

@Model
public final class ActiveScheduleEntity {
    @Attribute(.unique) public var id: Int64
    public var schedule: UUID
    public var type: String
    public var data: String?

    public init(id: Int64 = 0, schedule: UUID, type: String, data: String? = nil) {
        self.id = id
        self.schedule = schedule
        self.type = type
        self.data = data
    }
}
