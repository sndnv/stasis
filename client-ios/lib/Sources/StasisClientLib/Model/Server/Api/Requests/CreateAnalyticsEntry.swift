import Foundation

public struct CreateAnalyticsEntry: Sendable, Equatable, Hashable, Codable {
    public let entry: AnalyticsEntry.AsJson

    public init(entry: AnalyticsEntry.AsJson) {
        self.entry = entry
    }
}
