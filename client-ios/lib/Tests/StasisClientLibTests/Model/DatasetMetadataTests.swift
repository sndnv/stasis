import Foundation
@testable import StasisClientLib
import StasisClientLibTestSupport
import Testing

@Suite("DatasetMetadata")
struct DatasetMetadataTests {
    @Test("retrieves metadata for individual files (new and updated)")
    func collectNewAndUpdated() async throws {
        let mockApi = MockServerApiEndpointClient()
        let clients: any Clients = StaticClients(api: mockApi, core: MockServerCoreEndpointClient())

        let metadata = DatasetMetadata(
            contentChanged: [Fixtures.Metadata.fileOne.path: Fixtures.Metadata.fileOne],
            metadataChanged: [Fixtures.Metadata.fileTwo.path: Fixtures.Metadata.fileTwo],
            filesystem: FilesystemMetadata(entities: [
                Fixtures.Metadata.fileOne.path: .new,
                Fixtures.Metadata.fileTwo.path: .updated
            ])
        )

        let fileOneMetadata = try await metadata.collect(entity: Fixtures.Metadata.fileOne.path, clients: clients)
        let fileTwoMetadata = try await metadata.collect(entity: Fixtures.Metadata.fileTwo.path, clients: clients)

        let calls = await mockApi.calls
        #expect(calls.metadataWithIdRetrieved == 0)
        #expect(calls.metadataWithEntryRetrieved == 0)

        #expect(metadata.contentChangedBytes == Fixtures.Metadata.fileOne.fileSize)
        #expect(fileOneMetadata == Fixtures.Metadata.fileOne)
        #expect(fileTwoMetadata == Fixtures.Metadata.fileTwo)
    }

    @Test("throws when collecting missing metadata for new/updated entities")
    func collectMissingNewOrUpdated() async {
        let clients: any Clients = StaticClients(
            api: MockServerApiEndpointClient(),
            core: MockServerCoreEndpointClient()
        )

        let metadata = DatasetMetadata(
            contentChanged: [:],
            metadataChanged: [:],
            filesystem: FilesystemMetadata(entities: [
                Fixtures.Metadata.fileOne.path: .new,
                Fixtures.Metadata.fileTwo.path: .updated
            ])
        )

        await #expect(throws: DatasetMetadataCollectError.self) {
            try await metadata.collect(entity: Fixtures.Metadata.fileOne.path, clients: clients)
        }
        await #expect(throws: DatasetMetadataCollectError.self) {
            try await metadata.collect(entity: Fixtures.Metadata.fileTwo.path, clients: clients)
        }
    }

    @Test("collects metadata for existing entities via dataset entries")
    func collectExisting() async throws {
        let previousEntry = UUID()
        let mockApi = MockServerApiEndpointClient()

        let previousMetadata = DatasetMetadata(
            contentChanged: [Fixtures.Metadata.fileOne.path: Fixtures.Metadata.fileOne],
            metadataChanged: [Fixtures.Metadata.fileTwo.path: Fixtures.Metadata.fileTwo],
            filesystem: FilesystemMetadata(entities: [
                Fixtures.Metadata.fileOne.path: .new,
                Fixtures.Metadata.fileTwo.path: .updated
            ])
        )
        await mockApi.setDatasetMetadataOverride(previousEntry, previousMetadata)

        let clients: any Clients = StaticClients(api: mockApi, core: MockServerCoreEndpointClient())

        let current = DatasetMetadata(
            contentChanged: [:],
            metadataChanged: [:],
            filesystem: FilesystemMetadata(entities: [
                Fixtures.Metadata.fileOne.path: .existing(entry: previousEntry),
                Fixtures.Metadata.fileTwo.path: .existing(entry: previousEntry)
            ])
        )

        let fileOneMetadata = try await current.collect(entity: Fixtures.Metadata.fileOne.path, clients: clients)
        let fileTwoMetadata = try await current.collect(entity: Fixtures.Metadata.fileTwo.path, clients: clients)

        let calls = await mockApi.calls
        #expect(calls.metadataWithIdRetrieved == 2)
        #expect(fileOneMetadata == Fixtures.Metadata.fileOne)
        #expect(fileTwoMetadata == Fixtures.Metadata.fileTwo)
    }

    @Test("throws when existing-entity lookup yields no metadata")
    func collectExistingMissing() async {
        let previousEntry = UUID()
        let mockApi = MockServerApiEndpointClient()
        let clients: any Clients = StaticClients(api: mockApi, core: MockServerCoreEndpointClient())

        let current = DatasetMetadata(
            contentChanged: [:],
            metadataChanged: [:],
            filesystem: FilesystemMetadata(entities: [
                Fixtures.Metadata.fileOne.path: .existing(entry: previousEntry)
            ])
        )

        await #expect(throws: DatasetMetadataCollectError.self) {
            try await current.collect(entity: Fixtures.Metadata.fileOne.path, clients: clients)
        }
    }

    @Test("require returns metadata for new/updated entities")
    func requireMetadata() async throws {
        let clients: any Clients = StaticClients(
            api: MockServerApiEndpointClient(),
            core: MockServerCoreEndpointClient()
        )

        let metadata = DatasetMetadata(
            contentChanged: [Fixtures.Metadata.fileOne.path: Fixtures.Metadata.fileOne],
            metadataChanged: [Fixtures.Metadata.fileTwo.path: Fixtures.Metadata.fileTwo],
            filesystem: FilesystemMetadata(entities: [
                Fixtures.Metadata.fileOne.path: .new,
                Fixtures.Metadata.fileTwo.path: .updated
            ])
        )

        let fileOne = try await metadata.require(entity: Fixtures.Metadata.fileOne.path, clients: clients)
        let fileTwo = try await metadata.require(entity: Fixtures.Metadata.fileTwo.path, clients: clients)
        #expect(fileOne == Fixtures.Metadata.fileOne)
        #expect(fileTwo == Fixtures.Metadata.fileTwo)
    }

    @Test("collect errors expose human-readable descriptions")
    func collectErrorDescriptions() {
        let entry = UUID()

        let missingForEntity = DatasetMetadataCollectError.missingMetadataForEntity(entity: "test")
        #expect(missingForEntity.errorDescription == "Metadata for entity [test] not found")

        let missingInEntry = DatasetMetadataCollectError.missingMetadataForEntityInEntry(
            entity: "test", entry: entry
        )
        #expect(
            missingInEntry.errorDescription
                == "Expected metadata for entity [test] but none was found in metadata for entry [\(entry.uuidString)]"
        )
        #expect(missingInEntry.localizedDescription == missingInEntry.errorDescription)

        let requiredMissing = DatasetMetadataCollectError.requiredMetadataMissing(entity: "test")
        #expect(requiredMissing.errorDescription == "Required metadata for entity [test] not found")
    }

    @Test("require throws when metadata is missing")
    func requireMissing() async {
        let clients: any Clients = StaticClients(
            api: MockServerApiEndpointClient(),
            core: MockServerCoreEndpointClient()
        )
        let metadata = DatasetMetadata.empty()
        await #expect(throws: DatasetMetadataCollectError.self) {
            try await metadata.require(entity: Fixtures.Metadata.fileOne.path, clients: clients)
        }
    }
}

private extension EntityMetadata {
    var fileSize: Int64 {
        switch self {
        case .file(let f): f.size
        case .directory, .library: 0
        }
    }
}
