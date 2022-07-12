package stasis.client.tracking

trait TrackerViews {
  def backup: BackupTracker.View
  def recovery: RecoveryTracker.View
  def server: ServerTracker.View
}
