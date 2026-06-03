import Foundation
import fsi
@testable import StasisClientLib
import Testing

@Suite("DefaultSearch")
struct DefaultSearchTests {
    @Test("performs searches on dataset metadata")
    func performsSearches() async throws {
        let matchingDefinition = UUID()
        let nonMatchingDefinition = UUID()
        let missingEntryDefinition = UUID()

        let matchingEntry = UUID()
        let nonMatchingEntry = UUID()

        let searchTerm = "test-file-name"

        let definitions = [
            TestGenerators.definition(id: matchingDefinition),
            TestGenerators.definition(id: nonMatchingDefinition),
            TestGenerators.definition(id: missingEntryDefinition)
        ]

        let matchingFiles: [String: FilesystemMetadata.EntityState] = [
            "/\(searchTerm)-01": .new,
            "/\(searchTerm)-02": .updated,
            "/other-\(searchTerm)": .existing(entry: UUID()),
            "/\(searchTerm)": .new
        ]

        let nonMatchingFiles: [String: FilesystemMetadata.EntityState] = [
            "/other-name": .new
        ]

        let matchingMetadata = DatasetMetadata(
            contentChanged: [:],
            metadataChanged: [:],
            filesystem: .asTrie(underlying: TrieIndex(matchingFiles.merging(nonMatchingFiles) { current, _ in current }))
        )

        let mockApi = MockServerApiEndpointClient()
        await mockApi.setDatasetDefinitionsOverride(definitions)
        await mockApi.setLatestEntryOverride(matchingDefinition, matchingEntry)
        await mockApi.setLatestEntryOverride(nonMatchingDefinition, nonMatchingEntry)
        await mockApi.setLatestEntryOverride(missingEntryDefinition, nil)
        await mockApi.setDatasetMetadataOverride(matchingEntry, matchingMetadata)

        let search = DefaultSearch(api: mockApi)

        let pattern = try NSRegularExpression(pattern: ".*\(searchTerm).*")
        let result = try await search.search(query: pattern, until: nil)

        #expect(result.definitions.count == definitions.count)

        let matchingResult = try #require(result.definitions[matchingDefinition].flatMap { $0 })
        #expect(matchingResult.matches == matchingFiles)

        #expect(result.definitions[nonMatchingDefinition] == .some(nil))
        #expect(result.definitions[missingEntryDefinition] == .some(nil))

        let calls = await mockApi.calls
        #expect(calls.entryRetrieved == 0)
        #expect(calls.entryLatestRetrieved == 3)
        #expect(calls.entryCreated == 0)
        #expect(calls.entryDeleted == 0)
        #expect(calls.entriesRetrieved == 0)
        #expect(calls.definitionCreated == 0)
        #expect(calls.definitionUpdated == 0)
        #expect(calls.definitionDeleted == 0)
        #expect(calls.definitionRetrieved == 0)
        #expect(calls.definitionsRetrieved == 1)
        #expect(calls.publicSchedulesRetrieved == 0)
        #expect(calls.publicScheduleRetrieved == 0)
        #expect(calls.metadataWithIdRetrieved == 0)
        #expect(calls.metadataWithEntryRetrieved == 2)
        #expect(calls.userRetrieved == 0)
        #expect(calls.userSaltReset == 0)
        #expect(calls.userPasswordUpdated == 0)
        #expect(calls.deviceRetrieved == 0)
        #expect(calls.deviceKeyPushed == 0)
        #expect(calls.deviceKeyPulled == 0)
        #expect(calls.deviceKeyExistsChecked == 0)
        #expect(calls.pinged == 0)
        #expect(calls.commandsRetrieved == 0)
        #expect(calls.analyticsEntriesSent == 0)
    }
}
