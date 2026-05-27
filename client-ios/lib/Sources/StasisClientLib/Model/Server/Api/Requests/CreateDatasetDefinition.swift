import Foundation

public struct CreateDatasetDefinition: Sendable, Equatable, Hashable, Codable {
    public let info: String
    public let device: DeviceId
    public let redundantCopies: Int
    public let existingVersions: DatasetDefinition.Retention
    public let removedVersions: DatasetDefinition.Retention

    public init(
        info: String,
        device: DeviceId,
        redundantCopies: Int,
        existingVersions: DatasetDefinition.Retention,
        removedVersions: DatasetDefinition.Retention
    ) {
        self.info = info
        self.device = device
        self.redundantCopies = redundantCopies
        self.existingVersions = existingVersions
        self.removedVersions = removedVersions
    }
}
