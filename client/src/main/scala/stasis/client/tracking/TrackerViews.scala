package stasis.client.tracking

trait TrackerViews {
  def backup: BackupTracker.View with BackupTracker.Manage
  def recovery: RecoveryTracker.View with RecoveryTracker.Manage
  def server: ServerTracker.View
}
