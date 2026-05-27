import Foundation
@testable import StasisClientLib
import Testing

@Suite("DatasetEntry")
struct DatasetEntryTests {
    private let encoder = JSONCoders.encoder()
    private let decoder = JSONCoders.decoder()

    private static let now: Date = {
        let formatter = ISO8601DateFormatter()
        return formatter.date(from: "2020-01-02T03:04:05Z")!
    }()

    private static let defaultEntry = DatasetEntry(
        id: UUID(uuidString: "71a357e7-856f-4ae1-9d4d-abf424daddcd")!,
        definition: UUID(uuidString: "03455212-735b-444a-9886-6762432ebae9")!,
        device: UUID(uuidString: "0fabfb8e-aa45-4e9e-bca1-cca20b09097d")!,
        data: [
            UUID(uuidString: "87f08176-958e-411d-913e-b29798053030")!,
            UUID(uuidString: "0b2ca24f-f029-4733-8a73-8f835245d110")!,
            UUID(uuidString: "4cc26129-8921-4c1b-a207-f6b0edc1b4ee")!
        ],
        metadata: UUID(uuidString: "56179ef2-7897-488f-9cf0-6aa08c57b259")!,
        changes: nil,
        size: nil,
        created: now
    )

    private static let entryWithChangesAndSize = DatasetEntry(
        id: defaultEntry.id,
        definition: defaultEntry.definition,
        device: defaultEntry.device,
        data: defaultEntry.data,
        metadata: defaultEntry.metadata,
        changes: 1,
        size: 2,
        created: now
    )

    @Test("round-trips as JSON (default)")
    func roundTripsDefault() throws {
        let encoded = try encoder.encode(Self.defaultEntry)
        let decoded = try decoder.decode(DatasetEntry.self, from: encoded)
        #expect(decoded == Self.defaultEntry)
    }

    @Test("round-trips as JSON (with changes and size)")
    func roundTripsWithChangesAndSize() throws {
        let encoded = try encoder.encode(Self.entryWithChangesAndSize)
        let decoded = try decoder.decode(DatasetEntry.self, from: encoded)
        #expect(decoded == Self.entryWithChangesAndSize)
    }

    @Test("fails to decode JSON missing required UUIDs")
    func failsOnMissingUUIDs() {
        let json = #"{"info":"test"}"#
        #expect(throws: DecodingError.self) {
            try decoder.decode(DatasetEntry.self, from: Data(json.utf8))
        }
    }
}
