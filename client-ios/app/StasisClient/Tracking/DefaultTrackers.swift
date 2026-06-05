import StasisClientLib

public struct DefaultTrackers: Sendable {
    public let backup: DefaultBackupTracker
    public let recovery: DefaultRecoveryTracker
    public let server: DefaultServerTracker

    public init(
        backup: DefaultBackupTracker,
        recovery: DefaultRecoveryTracker,
        server: DefaultServerTracker
    ) {
        self.backup = backup
        self.recovery = recovery
        self.server = server
    }
}

public struct TrackerViews: Sendable {
    public let backup: any BackupTrackerView
    public let recovery: any RecoveryTrackerView
    public let server: any ServerTrackerView

    public init(
        backup: any BackupTrackerView,
        recovery: any RecoveryTrackerView,
        server: any ServerTrackerView
    ) {
        self.backup = backup
        self.recovery = recovery
        self.server = server
    }
}

public extension DefaultTrackers {
    var asViews: TrackerViews {
        TrackerViews(backup: backup, recovery: recovery, server: server)
    }
}
