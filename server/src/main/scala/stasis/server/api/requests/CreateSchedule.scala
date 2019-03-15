package stasis.server.api.requests

import java.time.LocalTime

import stasis.server.model.schedules.Schedule

import scala.concurrent.duration.FiniteDuration

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
