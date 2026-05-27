import Foundation

public struct UpdateDatasetDefinition: Sendable, Equatable, Hashable, Codable {
    public let info: String
    public let redundantCopies: Int
    public let existingVersions: DatasetDefinition.Retention
    public let removedVersions: DatasetDefinition.Retention

    public init(
        info: String,
        redundantCopies: Int,
        existingVersions: DatasetDefinition.Retention,
        removedVersions: DatasetDefinition.Retention
    ) {
        self.info = info
        self.redundantCopies = redundantCopies
        self.existingVersions = existingVersions
        self.removedVersions = removedVersions
    }
}
