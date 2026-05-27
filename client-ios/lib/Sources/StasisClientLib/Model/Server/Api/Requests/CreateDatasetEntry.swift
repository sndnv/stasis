import Foundation

public struct CreateDatasetEntry: Sendable, Equatable, Hashable, Codable {
    public let definition: DatasetDefinitionId
    public let device: DeviceId
    public let data: Set<CrateId>
    public let metadata: CrateId
    public let changes: Int64?
    public let size: Int64?

    public init(
        definition: DatasetDefinitionId,
        device: DeviceId,
        data: Set<CrateId>,
        metadata: CrateId,
        changes: Int64?,
        size: Int64?
    ) {
        self.definition = definition
        self.device = device
        self.data = data
        self.metadata = metadata
        self.changes = changes
        self.size = size
    }
}
