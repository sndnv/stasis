import Foundation

public typealias DatasetDefinitionId = UUID

public struct DatasetDefinition: Sendable, Equatable, Hashable, Codable {
    public let id: DatasetDefinitionId
    public let info: String
    public let device: DeviceId
    public let redundantCopies: Int
    public let existingVersions: Retention
    public let removedVersions: Retention
    public let created: Date
    public let updated: Date

    public init(
        id: DatasetDefinitionId,
        info: String,
        device: DeviceId,
        redundantCopies: Int,
        existingVersions: Retention,
        removedVersions: Retention,
        created: Date,
        updated: Date
    ) {
        self.id = id
        self.info = info
        self.device = device
        self.redundantCopies = redundantCopies
        self.existingVersions = existingVersions
        self.removedVersions = removedVersions
        self.created = created
        self.updated = updated
    }

    public struct Retention: Sendable, Equatable, Hashable, Codable {
        public let policy: Policy
        public let duration: SecondsDuration

        public init(policy: Policy, duration: SecondsDuration) {
            self.policy = policy
            self.duration = duration
        }

        public enum Policy: Sendable, Equatable, Hashable {
            case atMost(versions: Int)
            case latestOnly
            case all
        }
    }
}

extension DatasetDefinition.Retention.Policy: Codable {
    private enum CodingKeys: String, CodingKey {
        case policyType
        case versions
    }

    private enum PolicyType: String, Codable {
        case atMost = "at-most"
        case latestOnly = "latest-only"
        case all
    }

    public init(from decoder: any Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        let policyType = try container.decode(PolicyType.self, forKey: .policyType)
        switch policyType {
        case .atMost:
            let versions = try container.decode(Int.self, forKey: .versions)
            self = .atMost(versions: versions)
        case .latestOnly:
            self = .latestOnly
        case .all:
            self = .all
        }
    }

    public func encode(to encoder: any Encoder) throws {
        var container = encoder.container(keyedBy: CodingKeys.self)
        switch self {
        case .atMost(let versions):
            try container.encode(PolicyType.atMost, forKey: .policyType)
            try container.encode(versions, forKey: .versions)
        case .latestOnly:
            try container.encode(PolicyType.latestOnly, forKey: .policyType)
        case .all:
            try container.encode(PolicyType.all, forKey: .policyType)
        }
    }
}
