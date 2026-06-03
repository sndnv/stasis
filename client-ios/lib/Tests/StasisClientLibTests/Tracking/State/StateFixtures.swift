import Foundation
@testable import StasisClientLib

extension Fixtures {
    enum State {
        private static let fileOneMetadata: EntityMetadata = Fixtures.Metadata.fileOne
        private static let fileTwoMetadata: EntityMetadata = Fixtures.Metadata.fileTwo
        private static let fileThreeMetadata: EntityMetadata = Fixtures.Metadata.fileThree

        private static let fileOnePath = URL(fileURLWithPath: fileOneMetadata.path)
        private static let fileTwoPath = URL(fileURLWithPath: fileTwoMetadata.path)
        private static let fileThreePath = URL(fileURLWithPath: fileThreeMetadata.path)

        private static let timestamp = Date(timeIntervalSince1970: 1_716_000_000)

        static let backupOneState = BackupState(
            operation: UUID(uuidString: "0a8e5c6a-1c1b-4f2a-9c5e-9f80ad1c8a4d")!,
            definition: UUID(uuidString: "1d6f8b3c-2b88-4d2f-9a4d-7c5d4f9c8b2e")!,
            started: timestamp,
            entities: BackupState.Entities(
                discovered: [fileOnePath],
                unmatched: ["a", "b", "c"],
                examined: [fileTwoPath],
                skipped: [fileTwoPath],
                collected: [
                    fileOnePath: try! SourceEntity(
                        path: fileOnePath,
                        existingMetadata: fileOneMetadata,
                        currentMetadata: fileOneMetadata
                    )
                ],
                pending: [
                    fileTwoPath: BackupState.PendingSourceEntity(expectedParts: 1, processedParts: 2)
                ],
                processed: [
                    fileOnePath: BackupState.ProcessedSourceEntity(
                        expectedParts: 1, processedParts: 1, metadata: .left(fileOneMetadata)
                    ),
                    fileTwoPath: BackupState.ProcessedSourceEntity(
                        expectedParts: 0, processedParts: 0, metadata: .right(fileTwoMetadata)
                    )
                ],
                failed: [fileThreePath: "x"]
            ),
            metadataCollected: timestamp,
            metadataPushed: timestamp,
            failures: ["y", "z"],
            completed: timestamp
        )

        static let backupTwoState = BackupState(
            operation: UUID(uuidString: "2e7f9c5d-3c99-4e3b-8b6f-8d6e3a8e9c1f")!,
            definition: UUID(uuidString: "3f81ade4-4daa-4f4c-7a7e-9e7f4b7fad2a")!,
            started: timestamp,
            entities: .empty(),
            metadataCollected: nil,
            metadataPushed: nil,
            failures: [],
            completed: nil
        )

        static let recoveryOneState = RecoveryState(
            operation: UUID(uuidString: "4a92bf85-5ebb-4faf-6b8f-af80bc8fbe3b")!,
            started: timestamp,
            entities: RecoveryState.Entities(
                examined: [fileOnePath, fileTwoPath, fileThreePath],
                collected: [
                    fileOnePath: try! TargetEntity(
                        path: fileOnePath,
                        destination: .default,
                        existingMetadata: fileOneMetadata,
                        currentMetadata: fileOneMetadata
                    )
                ],
                pending: [
                    fileThreePath: RecoveryState.PendingTargetEntity(expectedParts: 3, processedParts: 1)
                ],
                processed: [
                    fileOnePath: RecoveryState.ProcessedTargetEntity(expectedParts: 1, processedParts: 1)
                ],
                metadataApplied: [fileOnePath],
                failed: [fileThreePath: "x"]
            ),
            failures: ["y", "z"],
            completed: timestamp
        )

        static let recoveryTwoState = RecoveryState(
            operation: UUID(uuidString: "5ba3c096-6fcc-4abf-7c90-bf91cd9fcf4c")!,
            started: timestamp,
            entities: RecoveryState.Entities(
                examined: [fileOnePath, fileTwoPath, fileThreePath],
                collected: [
                    fileOnePath: try! TargetEntity(
                        path: fileOnePath,
                        destination: .directory(path: fileOnePath, keepDefaultStructure: true),
                        existingMetadata: fileOneMetadata,
                        currentMetadata: fileOneMetadata
                    )
                ],
                pending: [
                    fileThreePath: RecoveryState.PendingTargetEntity(expectedParts: 3, processedParts: 1)
                ],
                processed: [
                    fileOnePath: RecoveryState.ProcessedTargetEntity(expectedParts: 1, processedParts: 1)
                ],
                metadataApplied: [fileOnePath],
                failed: [fileThreePath: "x"]
            ),
            failures: ["y", "z"],
            completed: timestamp
        )
    }
}
