import Foundation

public struct CreatedDatasetEntry: Sendable, Equatable, Hashable, Codable {
    public let entry: DatasetEntryId

    public init(entry: DatasetEntryId) {
        self.entry = entry
    }
}
