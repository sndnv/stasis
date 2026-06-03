import Foundation

public protocol Search: Sendable {
    func search(query: NSRegularExpression, until: Date?) async throws -> SearchResult
}

public struct SearchResult: Sendable, Equatable, Hashable {
    public let definitions: [DatasetDefinitionId: DatasetDefinitionResult?]

    public init(definitions: [DatasetDefinitionId: DatasetDefinitionResult?]) {
        self.definitions = definitions
    }
}

public struct DatasetDefinitionResult: Sendable, Equatable, Hashable {
    public let definitionInfo: String
    public let entryId: DatasetEntryId
    public let entryCreated: Date
    public let matches: [String: FilesystemMetadata.EntityState]

    public init(
        definitionInfo: String,
        entryId: DatasetEntryId,
        entryCreated: Date,
        matches: [String: FilesystemMetadata.EntityState]
    ) {
        self.definitionInfo = definitionInfo
        self.entryId = entryId
        self.entryCreated = entryCreated
        self.matches = matches
    }
}
