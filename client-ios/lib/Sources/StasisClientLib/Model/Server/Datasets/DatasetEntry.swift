import Foundation

public typealias DatasetEntryId = UUID

public struct DatasetEntry: Sendable, Equatable, Hashable, Codable {
    public let id: DatasetEntryId
    public let definition: DatasetDefinitionId
    public let device: DeviceId
    public let data: Set<CrateId>
    public let metadata: CrateId
    public let changes: Int64?
    public let size: Int64?
    public let created: Date

    public init(
        id: DatasetEntryId,
        definition: DatasetDefinitionId,
        device: DeviceId,
        data: Set<CrateId>,
        metadata: CrateId,
        changes: Int64?,
        size: Int64?,
        created: Date
    ) {
        self.id = id
        self.definition = definition
        self.device = device
        self.data = data
        self.metadata = metadata
        self.changes = changes
        self.size = size
        self.created = created
    }
}
