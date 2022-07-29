package stasis.client.service.components

import stasis.client.tracking.Trackers
import stasis.client.tracking.state.serdes._
import stasis.client.tracking.state.{BackupState, RecoveryState}
import stasis.client.tracking.trackers.{DefaultBackupTracker, DefaultRecoveryTracker, DefaultServerTracker}
import stasis.core.persistence.backends.file.EventLogFileBackend
import stasis.core.persistence.backends.file.state.StateStore
import stasis.core.persistence.backends.memory.EventLogMemoryBackend
import stasis.shared.ops.Operation

import java.nio.file.FileSystems
import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.Try

trait Tracking {
  def trackers: Trackers
}

object Tracking {
  def apply(base: Base): Future[Tracking] = {
    import BackupStateSerdes._
    import RecoveryStateSerdes._
    import base._

    Future.fromTry(
      Try {
        val parent = directory.appDirectory.resolve("state")
        val filesystem = FileSystems.getDefault

        val backupStateStore = StateStore[Map[Operation.Id, BackupState]](
          directory = parent.resolve("backups").toAbsolutePath.toString,
          filesystem = filesystem
        )

        val recoveryStateStore = StateStore[Map[Operation.Id, RecoveryState]](
          directory = parent.resolve("recoveries").toAbsolutePath.toString,
          filesystem = filesystem
        )

        new Tracking {
          override val trackers: Trackers = Trackers(
            backup = DefaultBackupTracker(
              maxRetention = rawConfig.getDuration("tracking.state.backup.max-retention").toMillis.millis,
              createBackend = state =>
                EventLogFileBackend(
                  config = EventLogFileBackend.Config(
                    name = s"backup-tracker-${java.util.UUID.randomUUID().toString}",
                    persistAfterEvents = rawConfig.getInt("tracking.state.backup.persist-after-events"),
                    persistAfterPeriod = rawConfig.getDuration("tracking.state.backup.persist-after-period").toMillis.millis
                  ),
                  initialState = state,
                  stateStore = backupStateStore
                )
            ),
            recovery = DefaultRecoveryTracker(
              maxRetention = rawConfig.getDuration("tracking.state.recovery.max-retention").toMillis.millis,
              createBackend = state =>
                EventLogFileBackend(
                  config = EventLogFileBackend.Config(
                    name = s"recovery-tracker-${java.util.UUID.randomUUID().toString}",
                    persistAfterEvents = rawConfig.getInt("tracking.state.recovery.persist-after-events"),
                    persistAfterPeriod = rawConfig.getDuration("tracking.state.recovery.persist-after-period").toMillis.millis
                  ),
                  initialState = state,
                  stateStore = recoveryStateStore
                )
            ),
            server = DefaultServerTracker(
              createBackend = state =>
                EventLogMemoryBackend(
                  name = s"server-tracker-${java.util.UUID.randomUUID().toString}",
                  initialState = state
                )
            )
          )
        }
      }
    )
  }
}
