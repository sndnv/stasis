import Foundation

public struct CreatedDatasetDefinition: Sendable, Equatable, Hashable, Codable {
    public let definition: DatasetDefinitionId

    public init(definition: DatasetDefinitionId) {
        self.definition = definition
    }
}
