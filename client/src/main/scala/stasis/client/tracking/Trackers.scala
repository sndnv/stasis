package stasis.client.tracking

final case class Trackers(
  backup: BackupTracker,
  recovery: RecoveryTracker,
  server: ServerTracker
) { parent =>
  def views: TrackerViews = new TrackerViews {
    override val backup: BackupTracker.View = parent.backup
    override val recovery: RecoveryTracker.View = parent.recovery
    override val server: ServerTracker.View = parent.server
  }
}
