package stasis.shared.api.requests

import java.time.LocalTime

import scala.concurrent.duration.FiniteDuration

import stasis.shared.model.schedules.Schedule

final case class UpdateSchedule(
  process: Schedule.Process,
  instant: LocalTime,
  interval: FiniteDuration,
  missed: Schedule.MissedAction,
  overlap: Schedule.OverlapAction
)

object UpdateSchedule {
  implicit class RequestToUpdatedSchedule(request: UpdateSchedule) {
    def toUpdatedSchedule(schedule: Schedule): Schedule =
      schedule.copy(
        process = request.process,
        instant = request.instant,
        interval = request.interval,
        missed = request.missed,
        overlap = request.overlap
      )
  }
}
