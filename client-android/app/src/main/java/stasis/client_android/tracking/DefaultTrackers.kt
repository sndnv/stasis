package stasis.client_android.tracking

data class DefaultTrackers(
    val backup: DefaultBackupTracker,
    val recovery: DefaultRecoveryTracker,
    val server: DefaultServerTracker
)
