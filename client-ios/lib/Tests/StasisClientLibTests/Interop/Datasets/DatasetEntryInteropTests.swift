import Foundation
@testable import StasisClientLib
import Testing

@Suite("DatasetEntry interop")
struct DatasetEntryInteropTests {
    @Test("decode and re-encode an entry")
    func entry() throws {
        try assert(
            domain: "datasets",
            resource: "DatasetEntry",
            matches: DatasetEntry(
                id: UUID(uuidString: "77eebe09-aea1-4e2e-9cd0-3b5919d43def")!,
                definition: UUID(uuidString: "6c041cb6-94af-4649-9091-8e39d330527e")!,
                device: UUID(uuidString: "caac9b3b-dbf9-41c1-9fee-3aa0e5bbb70b")!,
                data: [UUID(uuidString: "d0c7ddc7-f639-4686-a3b5-465c61cdf94b")!],
                metadata: UUID(uuidString: "6bb8d69c-5ae4-4876-9836-35e66a1c9ecb")!,
                changes: 42,
                size: 1048576,
                created: Date(timeIntervalSince1970: 1769072400)
            )
        )
    }

    @Test("decode and re-encode CreateDatasetEntry")
    func createDatasetEntry() throws {
        try assert(
            domain: "datasets",
            resource: "CreateDatasetEntry",
            matches: CreateDatasetEntry(
                definition: UUID(uuidString: "874e3e1e-a663-444d-b498-d771ecf5d0b7")!,
                device: UUID(uuidString: "caac9b3b-dbf9-41c1-9fee-3aa0e5bbb70b")!,
                data: [UUID(uuidString: "d0c7ddc7-f639-4686-a3b5-465c61cdf94b")!],
                metadata: UUID(uuidString: "6bb8d69c-5ae4-4876-9836-35e66a1c9ecb")!,
                changes: 13,
                size: 524288
            )
        )
    }

    @Test("decode and re-encode CreatedDatasetEntry")
    func createdDatasetEntry() throws {
        try assert(
            domain: "datasets",
            resource: "CreatedDatasetEntry",
            matches: CreatedDatasetEntry(
                entry: UUID(uuidString: "17a89e29-0f3b-48f3-8f6c-dd939864e9a9")!
            )
        )
    }
}
