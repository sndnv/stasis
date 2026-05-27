import Foundation

public typealias UserId = UUID

public struct User: Sendable, Equatable, Hashable, Codable {
    public let id: UserId
    public let salt: String
    public let active: Bool
    public let limits: Limits?
    public let permissions: Set<String>
    public let created: Date
    public let updated: Date

    public init(
        id: UserId,
        salt: String,
        active: Bool,
        limits: Limits?,
        permissions: Set<String>,
        created: Date,
        updated: Date
    ) {
        self.id = id
        self.salt = salt
        self.active = active
        self.limits = limits
        self.permissions = permissions
        self.created = created
        self.updated = updated
    }

    public struct Limits: Sendable, Equatable, Hashable, Codable {
        public let maxDevices: Int64
        public let maxCrates: Int64
        public let maxStorage: Int64
        public let maxStoragePerCrate: Int64
        public let maxRetention: SecondsDuration
        public let minRetention: SecondsDuration

        public init(
            maxDevices: Int64,
            maxCrates: Int64,
            maxStorage: Int64,
            maxStoragePerCrate: Int64,
            maxRetention: SecondsDuration,
            minRetention: SecondsDuration
        ) {
            self.maxDevices = maxDevices
            self.maxCrates = maxCrates
            self.maxStorage = maxStorage
            self.maxStoragePerCrate = maxStoragePerCrate
            self.maxRetention = maxRetention
            self.minRetention = minRetention
        }
    }
}
