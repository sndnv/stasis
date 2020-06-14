package stasis.test.specs.unit.server.model.mocks

import akka.actor.typed.{ActorSystem, SpawnProtocol}
import akka.util.Timeout
import stasis.core.persistence.backends.memory.MemoryBackend
import stasis.server.model.schedules.ScheduleStore
import stasis.shared.model.schedules.Schedule

object MockScheduleStore {
  def apply()(implicit system: ActorSystem[SpawnProtocol.Command], timeout: Timeout): ScheduleStore = {
    val backend: MemoryBackend[Schedule.Id, Schedule] =
      MemoryBackend[Schedule.Id, Schedule](
        s"mock-schedule-store-${java.util.UUID.randomUUID()}"
      )

    ScheduleStore(backend)(system.executionContext)
  }
}
