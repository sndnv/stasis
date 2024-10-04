package stasis.shared.api.requests

import java.time.LocalDateTime

import scala.concurrent.duration.FiniteDuration

import stasis.shared.model.schedules.Schedule

final case class CreateSchedule(
  info: String,
  isPublic: Boolean,
  start: LocalDateTime,
  interval: FiniteDuration
)

object CreateSchedule {
  implicit class RequestToSchedule(request: CreateSchedule) {
    def toSchedule: Schedule =
      Schedule(
        id = Schedule.generateId(),
        info = request.info,
        isPublic = request.isPublic,
        start = request.start,
        interval = request.interval
      )
  }
}
