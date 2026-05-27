import Foundation

public struct DatasetEntriesForDefinition: Sendable, Equatable, Hashable {
    public let entries: [DatasetEntryId: Date]
    public let latest: DatasetEntryId?

    public init(entries: [DatasetEntryId: Date], latest: DatasetEntryId?) {
        self.entries = entries
        self.latest = latest
    }

    public init(entries: [DatasetEntry]) {
        let mapping = Dictionary(uniqueKeysWithValues: entries.map { ($0.id, $0.created) })
        self.entries = mapping
        self.latest = mapping.max(by: { $0.value < $1.value })?.key
    }

    public static func empty() -> DatasetEntriesForDefinition {
        DatasetEntriesForDefinition(entries: [:], latest: nil)
    }

    public func with(entry: DatasetEntry) -> DatasetEntriesForDefinition {
        var updated = entries
        updated[entry.id] = entry.created
        let newLatest = updated.max(by: { $0.value < $1.value })?.key
        return DatasetEntriesForDefinition(entries: updated, latest: newLatest)
    }
}
