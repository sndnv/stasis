package stasis.test.specs.unit.server.model.mocks

import org.apache.pekko.actor.typed.ActorSystem
import org.apache.pekko.util.Timeout

import stasis.layers.persistence.memory.MemoryStore
import stasis.layers.telemetry.TelemetryContext
import stasis.server.model.schedules.ScheduleStore
import stasis.shared.model.schedules.Schedule

object MockScheduleStore {
  def apply()(implicit
    system: ActorSystem[Nothing],
    telemetry: TelemetryContext,
    timeout: Timeout
  ): ScheduleStore = {
    val backend: MemoryStore[Schedule.Id, Schedule] =
      MemoryStore[Schedule.Id, Schedule](
        s"mock-schedule-store-${java.util.UUID.randomUUID()}"
      )

    ScheduleStore(backend)(system.executionContext)
  }
}
