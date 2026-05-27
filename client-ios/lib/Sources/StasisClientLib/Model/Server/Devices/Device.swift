import Foundation

public typealias DeviceId = UUID

public struct Device: Sendable, Equatable, Hashable, Codable {
    public let id: DeviceId
    public let name: String
    public let node: NodeId
    public let owner: UserId
    public let active: Bool
    public let limits: Limits?
    public let created: Date
    public let updated: Date

    public init(
        id: DeviceId,
        name: String,
        node: NodeId,
        owner: UserId,
        active: Bool,
        limits: Limits?,
        created: Date,
        updated: Date
    ) {
        self.id = id
        self.name = name
        self.node = node
        self.owner = owner
        self.active = active
        self.limits = limits
        self.created = created
        self.updated = updated
    }

    public struct Limits: Sendable, Equatable, Hashable, Codable {
        public let maxCrates: Int64
        public let maxStorage: Int64
        public let maxStoragePerCrate: Int64
        public let maxRetention: SecondsDuration
        public let minRetention: SecondsDuration

        public init(
            maxCrates: Int64,
            maxStorage: Int64,
            maxStoragePerCrate: Int64,
            maxRetention: SecondsDuration,
            minRetention: SecondsDuration
        ) {
            self.maxCrates = maxCrates
            self.maxStorage = maxStorage
            self.maxStoragePerCrate = maxStoragePerCrate
            self.maxRetention = maxRetention
            self.minRetention = minRetention
        }
    }
}
