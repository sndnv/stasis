package stasis.server.model.schedules

import java.time.LocalTime

import scala.concurrent.duration._

final case class Schedule(
  id: Schedule.Id,
  process: Schedule.Process,
  instant: LocalTime,
  interval: FiniteDuration,
  missed: Schedule.MissedAction,
  overlap: Schedule.OverlapAction
)

object Schedule {
  type Id = java.util.UUID

  def generateId(): Id = java.util.UUID.randomUUID()

  sealed trait Process
  object Process {
    case object Backup extends Process
    case object Expiration extends Process
  }

  sealed trait MissedAction
  object MissedAction {
    case object ExecuteImmediately extends MissedAction
    case object ExecuteNext extends MissedAction
  }

  sealed trait OverlapAction
  object OverlapAction {
    case object CancelExisting extends OverlapAction
    case object CancelNew extends OverlapAction
    case object ExecuteAnyway extends OverlapAction
  }
}
