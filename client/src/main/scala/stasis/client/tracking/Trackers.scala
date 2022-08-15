package stasis.client.tracking

final case class Trackers(
  backup: BackupTracker,
  recovery: RecoveryTracker,
  server: ServerTracker
) { parent =>
  def views: TrackerViews = new TrackerViews {
    override val backup: BackupTracker.View with BackupTracker.Manage = parent.backup
    override val recovery: RecoveryTracker.View with RecoveryTracker.Manage = parent.recovery
    override val server: ServerTracker.View = parent.server
  }
}
