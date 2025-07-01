package stasis.server.persistence.schedules

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

import org.apache.pekko.Done
import org.apache.pekko.actor.typed.ActorSystem
import org.apache.pekko.util.Timeout

import io.github.sndnv.layers.persistence.KeyValueStore
import io.github.sndnv.layers.persistence.memory.MemoryStore
import io.github.sndnv.layers.persistence.migration.Migration
import io.github.sndnv.layers.telemetry.mocks.MockTelemetryContext
import io.github.sndnv.layers.telemetry.TelemetryContext
import stasis.shared.model.schedules.Schedule

class MockScheduleStore(
  underlying: KeyValueStore[Schedule.Id, Schedule]
)(implicit system: ActorSystem[Nothing])
    extends ScheduleStore {
  override protected implicit val ec: ExecutionContext = system.executionContext

  override val name: String = underlying.name()

  override val migrations: Seq[Migration] = Seq.empty

  override protected[schedules] def put(schedule: Schedule): Future[Done] =
    underlying.put(schedule.id, schedule)

  override protected[schedules] def delete(schedule: Schedule.Id): Future[Boolean] =
    underlying.delete(schedule)

  override protected[schedules] def get(schedule: Schedule.Id): Future[Option[Schedule]] =
    underlying.get(schedule)

  override protected[schedules] def list(): Future[Seq[Schedule]] =
    underlying.entries.map(_.values.toSeq)

  override def init(): Future[Done] = underlying.init()

  override def drop(): Future[Done] = underlying.drop()
}

object MockScheduleStore {
  def apply()(implicit system: ActorSystem[Nothing], timeout: Timeout): ScheduleStore = {
    implicit val telemetry: TelemetryContext = MockTelemetryContext()

    val underlying = MemoryStore[Schedule.Id, Schedule](s"mock-schedule-store-${java.util.UUID.randomUUID()}")

    new MockScheduleStore(underlying)
  }
}
