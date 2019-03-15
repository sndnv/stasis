package stasis.server.api.requests

import java.time.LocalTime

import stasis.server.model.schedules.Schedule

import scala.concurrent.duration.FiniteDuration

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
