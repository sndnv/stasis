import Foundation
@testable import StasisClientLib
import Testing

@Suite("DatasetDefinition interop")
struct DatasetDefinitionInteropTests {
    @Test("decode and re-encode a definition")
    func definition() throws {
        try assert(
            domain: "datasets",
            resource: "DatasetDefinition",
            matches: DatasetDefinition(
                id: UUID(uuidString: "6c041cb6-94af-4649-9091-8e39d330527e")!,
                info: "primary backup definition",
                device: UUID(uuidString: "caac9b3b-dbf9-41c1-9fee-3aa0e5bbb70b")!,
                redundantCopies: 3,
                existingVersions: DatasetDefinition.Retention(
                    policy: .atMost(versions: 5),
                    duration: SecondsDuration(604800)
                ),
                removedVersions: DatasetDefinition.Retention(
                    policy: .latestOnly,
                    duration: SecondsDuration(2592000)
                ),
                created: Date(timeIntervalSince1970: 1768473000),
                updated: Date(timeIntervalSince1970: 1768920300)
            )
        )
    }

    @Test("decode and re-encode CreateDatasetDefinition")
    func createDatasetDefinition() throws {
        try assert(
            domain: "datasets",
            resource: "CreateDatasetDefinition",
            matches: CreateDatasetDefinition(
                info: "new backup definition",
                device: UUID(uuidString: "caac9b3b-dbf9-41c1-9fee-3aa0e5bbb70b")!,
                redundantCopies: 2,
                existingVersions: DatasetDefinition.Retention(
                    policy: .all,
                    duration: SecondsDuration(7776000)
                ),
                removedVersions: DatasetDefinition.Retention(
                    policy: .atMost(versions: 7),
                    duration: SecondsDuration(1209600)
                )
            )
        )
    }

    @Test("decode and re-encode UpdateDatasetDefinition")
    func updateDatasetDefinition() throws {
        try assert(
            domain: "datasets",
            resource: "UpdateDatasetDefinition",
            matches: UpdateDatasetDefinition(
                info: "updated backup definition",
                redundantCopies: 4,
                existingVersions: DatasetDefinition.Retention(
                    policy: .latestOnly,
                    duration: SecondsDuration(86400)
                ),
                removedVersions: DatasetDefinition.Retention(
                    policy: .atMost(versions: 3),
                    duration: SecondsDuration(432000)
                )
            )
        )
    }

    @Test("decode and re-encode CreatedDatasetDefinition")
    func createdDatasetDefinition() throws {
        try assert(
            domain: "datasets",
            resource: "CreatedDatasetDefinition",
            matches: CreatedDatasetDefinition(
                definition: UUID(uuidString: "6d63a635-d012-4b28-9193-7bd870cc4093")!
            )
        )
    }
}
