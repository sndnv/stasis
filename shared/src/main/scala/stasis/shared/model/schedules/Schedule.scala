package stasis.shared.model.schedules

import java.time.Instant
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

import scala.concurrent.duration._

final case class Schedule(
  id: Schedule.Id,
  info: String,
  isPublic: Boolean,
  start: LocalDateTime,
  interval: FiniteDuration,
  created: Instant,
  updated: Instant
) {
  def nextInvocation: LocalDateTime = {
    val now = LocalDateTime.now()

    if (start.isBefore(now)) {
      val intervalMillis = math.max(interval.toMillis, 1)

      val difference = start.until(now, ChronoUnit.MILLIS)

      val invocations = difference / intervalMillis
      start.plus((invocations + 1) * intervalMillis, ChronoUnit.MILLIS)
    } else {
      start
    }
  }
}

object Schedule {
  type Id = java.util.UUID

  def generateId(): Id = java.util.UUID.randomUUID()
}
