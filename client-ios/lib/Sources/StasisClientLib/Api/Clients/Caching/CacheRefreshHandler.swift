import Foundation

public enum CacheRefreshTarget: Sendable, Equatable, Hashable {
    case allDatasetDefinitions
    case allDatasetEntries(definition: DatasetDefinitionId)
    case latestDatasetEntry(definition: DatasetDefinitionId)
    case individualDatasetDefinition(definition: DatasetDefinitionId)
    case individualDatasetEntry(entry: DatasetEntryId)

    public var name: String {
        switch self {
        case .allDatasetDefinitions: "all_dataset_definitions"
        case .allDatasetEntries: "all_dataset_entries"
        case .latestDatasetEntry: "latest_dataset_entry"
        case .individualDatasetDefinition: "individual_dataset_definition"
        case .individualDatasetEntry: "individual_dataset_entry"
        }
    }
}

public protocol CacheRefreshHandler: Sendable {
    func refreshNow(target: CacheRefreshTarget) async throws
    func stop() async
}

public enum CacheRefreshHandlerDefaults {
    public static let targets: [CacheRefreshTarget] = [.allDatasetDefinitions]
}
