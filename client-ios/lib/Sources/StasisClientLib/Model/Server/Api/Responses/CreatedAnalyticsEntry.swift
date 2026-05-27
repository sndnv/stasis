import Foundation

public struct CreatedAnalyticsEntry: Sendable, Equatable, Hashable, Codable {
    public let entry: UUID

    public init(entry: UUID) {
        self.entry = entry
    }
}
