package stasis.shared.api.requests

import java.time.LocalDateTime

import stasis.shared.model.schedules.Schedule

import scala.concurrent.duration.FiniteDuration

final case class UpdateSchedule(
  info: String,
  start: LocalDateTime,
  interval: FiniteDuration
)

object UpdateSchedule {
  implicit class RequestToUpdatedSchedule(request: UpdateSchedule) {
    def toUpdatedSchedule(schedule: Schedule): Schedule =
      schedule.copy(
        info = request.info,
        start = request.start,
        interval = request.interval
      )
  }
}
