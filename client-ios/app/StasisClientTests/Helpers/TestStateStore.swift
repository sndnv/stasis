import Foundation
import StasisClientLib

enum TestStateStore {
    static func backups() throws -> StateStore<[OperationId: BackupState]> {
        try StateStore(target: try tempDirectory(), serdes: BackupStateSerdes())
    }

    static func recoveries() throws -> StateStore<[OperationId: RecoveryState]> {
        try StateStore(target: try tempDirectory(), serdes: RecoveryStateSerdes())
    }

    private static func tempDirectory() throws -> URL {
        let url = FileManager.default.temporaryDirectory
            .appendingPathComponent("stasis-tests", isDirectory: true)
            .appendingPathComponent(UUID().uuidString, isDirectory: true)
        try FileManager.default.createDirectory(at: url, withIntermediateDirectories: true)
        return url
    }
}
