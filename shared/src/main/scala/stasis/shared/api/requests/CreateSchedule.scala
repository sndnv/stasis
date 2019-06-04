package stasis.shared.api.requests

import java.time.LocalTime

import scala.concurrent.duration.FiniteDuration

import stasis.shared.model.schedules.Schedule

final case class CreateSchedule(
  process: Schedule.Process,
  instant: LocalTime,
  interval: FiniteDuration,
  missed: Schedule.MissedAction,
  overlap: Schedule.OverlapAction
)

object CreateSchedule {
  implicit class RequestToSchedule(request: CreateSchedule) {
    def toSchedule: Schedule =
      Schedule(
        id = Schedule.generateId(),
        process = request.process,
        instant = request.instant,
        interval = request.interval,
        missed = request.missed,
        overlap = request.overlap
      )
  }
}
