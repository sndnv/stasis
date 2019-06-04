package stasis.test.specs.unit.server.model.mocks

import scala.concurrent.Future

import akka.Done
import akka.actor.ActorSystem
import akka.util.Timeout
import stasis.core.persistence.backends.memory.MemoryBackend
import stasis.server.model.schedules.ScheduleStore
import stasis.shared.model.schedules.Schedule

class MockScheduleStore()(implicit system: ActorSystem, timeout: Timeout) extends ScheduleStore {
  private val backend: MemoryBackend[Schedule.Id, Schedule] =
    MemoryBackend.untyped[Schedule.Id, Schedule](
      s"mock-schedule-store-${java.util.UUID.randomUUID()}"
    )

  override protected def create(schedule: Schedule): Future[Done] =
    backend.put(schedule.id, schedule)

  override protected def update(schedule: Schedule): Future[Done] =
    backend.put(schedule.id, schedule)

  override protected def delete(schedule: Schedule.Id): Future[Boolean] =
    backend.delete(schedule)

  override protected def get(schedule: Schedule.Id): Future[Option[Schedule]] =
    backend.get(schedule)

  override protected def list(): Future[Map[Schedule.Id, Schedule]] =
    backend.entries
}
