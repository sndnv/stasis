import Foundation
import SwiftData

@Model
public final class LocalScheduleEntity {
    @Attribute(.unique) public var id: UUID
    public var info: String
    public var start: String
    public var intervalSeconds: Int64
    public var created: Date

    public init(id: UUID, info: String, start: String, intervalSeconds: Int64, created: Date) {
        self.id = id
        self.info = info
        self.start = start
        self.intervalSeconds = intervalSeconds
        self.created = created
    }
}
