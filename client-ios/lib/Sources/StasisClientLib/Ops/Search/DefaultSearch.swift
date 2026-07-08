import Foundation

public final class DefaultSearch: Search {
    private let api: any ServerApiEndpointClient

    public init(api: any ServerApiEndpointClient) {
        self.api = api
    }

    public func search(query: NSRegularExpression, until: Date?) async throws -> SearchResult {
        let definitions = (try? await api.datasetDefinitions()) ?? []

        var results: [DatasetDefinitionId: DatasetDefinitionResult?] = [:]
        for definition in definitions {
            let resolved = try? await result(for: definition, query: query, until: until)
            results.updateValue(resolved ?? nil, forKey: definition.id)
        }

        return SearchResult(definitions: results)
    }

    private func result(
        for definition: DatasetDefinition,
        query: NSRegularExpression,
        until: Date?
    ) async throws -> DatasetDefinitionResult? {
        guard let entry = try await api.latestEntry(definition: definition.id, until: until) else {
            return nil
        }
        let matches = try await api.datasetMetadata(entry: entry).filesystem.search(query)
        guard !matches.isEmpty else { return nil }
        return DatasetDefinitionResult(
            definitionInfo: definition.info,
            entryId: entry.id,
            entryCreated: entry.created,
            matches: matches
        )
    }
}
