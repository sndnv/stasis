package stasis.client_android.tracking

data class TrackerViews(
    val backup: BackupTrackerView,
    val recovery: RecoveryTrackerView,
    val server: ServerTrackerView
)
