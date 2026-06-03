import Foundation
@testable import StasisClientLib
import Testing

@Suite("CacheRefreshHandler")
struct CacheRefreshHandlerTests {
    @Test("exposes a stable name for each refresh target")
    func exposesName() {
        let definition = UUID()
        let entry = UUID()

        #expect(CacheRefreshTarget.allDatasetDefinitions.name == "all_dataset_definitions")
        #expect(CacheRefreshTarget.allDatasetEntries(definition: definition).name == "all_dataset_entries")
        #expect(CacheRefreshTarget.latestDatasetEntry(definition: definition).name == "latest_dataset_entry")
        #expect(CacheRefreshTarget.individualDatasetDefinition(definition: definition).name == "individual_dataset_definition")
        #expect(CacheRefreshTarget.individualDatasetEntry(entry: entry).name == "individual_dataset_entry")
    }

    @Test("provides default refresh targets")
    func providesDefaultTargets() {
        #expect(CacheRefreshHandlerDefaults.targets == [.allDatasetDefinitions])
    }
}
