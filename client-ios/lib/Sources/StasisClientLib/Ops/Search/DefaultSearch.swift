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
            let entry = try await api.latestEntry(definition: definition.id, until: until)
            guard let entry else {
                results[definition.id] = .some(nil)
                continue
            }
            let metadata = try await api.datasetMetadata(entry: entry)
            let matches = metadata.filesystem.search(query)
            if matches.isEmpty {
                results[definition.id] = .some(nil)
            } else {
                results[definition.id] = .some(
                    DatasetDefinitionResult(
                        definitionInfo: definition.info,
                        entryId: entry.id,
                        entryCreated: entry.created,
                        matches: matches
                    )
                )
            }
        }

        return SearchResult(definitions: results)
    }
}
