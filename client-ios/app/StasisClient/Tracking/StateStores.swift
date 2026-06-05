import Foundation
import StasisClientLib

public enum StateStores {
    public static func backups() throws -> StateStore<[OperationId: BackupState]> {
        try StateStore(target: try directory(named: "backups"), serdes: BackupStateSerdes())
    }

    public static func recoveries() throws -> StateStore<[OperationId: RecoveryState]> {
        try StateStore(target: try directory(named: "recoveries"), serdes: RecoveryStateSerdes())
    }

    private static func directory(named name: String) throws -> URL {
        let root = try FileManager.default.url(
            for: .applicationSupportDirectory,
            in: .userDomainMask,
            appropriateFor: nil,
            create: true
        )
        return root.appendingPathComponent("state", isDirectory: true)
            .appendingPathComponent(name, isDirectory: true)
    }
}
