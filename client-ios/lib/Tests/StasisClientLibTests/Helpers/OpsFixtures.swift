import Foundation
@testable import StasisClientLib

extension Fixtures {
    enum Datasets {
        static let `default` = DatasetDefinition(
            id: UUID(uuidString: "f3c2b4d0-5a7e-4d11-9c1b-2f7a0d8e5c6a")!,
            info: "test-dataset",
            device: UUID(uuidString: "a1b2c3d4-e5f6-4708-8901-1a2b3c4d5e6f")!,
            redundantCopies: 1,
            existingVersions: .init(policy: .all, duration: SecondsDuration(3600)),
            removedVersions: .init(policy: .latestOnly, duration: SecondsDuration(3600)),
            created: Date(timeIntervalSince1970: 1_700_000_000),
            updated: Date(timeIntervalSince1970: 1_700_000_000)
        )
    }

    enum Entries {
        static let `default` = DatasetEntry(
            id: UUID(uuidString: "b9c8a7d6-e5f4-4302-9180-1f2e3d4c5b6a")!,
            definition: Datasets.default.id,
            device: Datasets.default.device,
            data: [UUID(uuidString: "c0d1e2f3-a4b5-4c67-8901-2a3b4c5d6e7f")!],
            metadata: UUID(uuidString: "d1e2f3a4-b5c6-4708-9012-3a4b5c6d7e8f")!,
            changes: 1,
            size: 1,
            created: Date(timeIntervalSince1970: 1_700_000_100)
        )
    }

    enum Secrets {
        static let `default` = DeviceSecret(
            user: SecretsConfigFixtures.testUser,
            device: SecretsConfigFixtures.testDevice,
            secret: Data("some-secret".utf8),
            target: SecretsConfigFixtures.testConfig
        )
    }
}
