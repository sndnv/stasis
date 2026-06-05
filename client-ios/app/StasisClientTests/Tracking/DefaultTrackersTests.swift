import Foundation
@testable import StasisClient
import StasisClientLib
import Testing

@Suite("DefaultTrackers")
struct DefaultTrackersTests {
    @Test("asViews exposes the same tracker instances")
    func asViewsForwards() throws {
        let backup = DefaultBackupTracker(store: try TestStateStore.backups())
        let recovery = DefaultRecoveryTracker(store: try TestStateStore.recoveries())
        let server = DefaultServerTracker()
        let trackers = DefaultTrackers(backup: backup, recovery: recovery, server: server)
        let views = trackers.asViews
        #expect(views.backup as AnyObject === backup)
        #expect(views.recovery as AnyObject === recovery)
        #expect(views.server as AnyObject === server)
    }
}
