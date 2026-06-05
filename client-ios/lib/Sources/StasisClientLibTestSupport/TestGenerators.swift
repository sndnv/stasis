import Foundation
import fsi
import StasisClientLib

public enum TestGenerators {
    public static func definition(
        id: DatasetDefinitionId = UUID(),
        device: DeviceId = UUID(),
        info: String = "test-definition"
    ) -> DatasetDefinition {
        DatasetDefinition(
            id: id,
            info: info,
            device: device,
            redundantCopies: 1,
            existingVersions: .init(policy: .all, duration: SecondsDuration(3)),
            removedVersions: .init(policy: .all, duration: SecondsDuration(3)),
            created: Date(),
            updated: Date()
        )
    }

    public static func entry(
        id: DatasetEntryId = UUID(),
        definition: DatasetDefinitionId = UUID()
    ) -> DatasetEntry {
        DatasetEntry(
            id: id,
            definition: definition,
            device: UUID(),
            data: [],
            metadata: UUID(),
            changes: 0,
            size: 0,
            created: Date()
        )
    }

    public static func schedule(
        id: ScheduleId = UUID(),
        isPublic: Bool = true
    ) -> Schedule {
        Schedule(
            id: id,
            info: "test-schedule",
            isPublic: isPublic,
            start: LocalDateTime("2026-05-23T12:34:56"),
            interval: SecondsDuration(60),
            created: Date(),
            updated: Date()
        )
    }

    public static func user() -> User {
        User(
            id: UUID(),
            salt: "test-salt",
            active: true,
            limits: nil,
            permissions: [],
            created: Date(),
            updated: Date()
        )
    }

    public static func device() -> Device {
        Device(
            id: UUID(),
            name: "test-device",
            node: UUID(),
            owner: UUID(),
            active: true,
            limits: nil,
            created: Date(),
            updated: Date()
        )
    }

    public static var emptyDatasetMetadata: DatasetMetadata {
        DatasetMetadata(
            contentChanged: [:],
            metadataChanged: [:],
            filesystem: .asTrie(underlying: TrieIndex<FilesystemMetadata.EntityState>())
        )
    }
}
