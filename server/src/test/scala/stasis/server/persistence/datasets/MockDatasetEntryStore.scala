package stasis.server.persistence.datasets

import java.time.Instant

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
import stasis.shared.model.datasets.DatasetDefinition
import stasis.shared.model.datasets.DatasetEntry
import stasis.shared.model.devices.Device

class MockDatasetEntryStore(
  underlying: KeyValueStore[DatasetEntry.Id, DatasetEntry]
)(implicit system: ActorSystem[Nothing])
    extends DatasetEntryStore {
  override protected implicit def ec: ExecutionContext = system.executionContext

  override val name: String = underlying.name()

  override val migrations: Seq[Migration] = Seq.empty

  override protected[datasets] def create(entry: DatasetEntry): Future[Done] =
    underlying.put(entry.id, entry)

  override protected[datasets] def delete(entry: DatasetEntry.Id): Future[Boolean] =
    underlying.delete(entry)

  override protected[datasets] def get(entry: DatasetEntry.Id): Future[Option[DatasetEntry]] =
    underlying.get(entry)

  override protected[datasets] def list(definition: DatasetDefinition.Id): Future[Seq[DatasetEntry]] =
    underlying.entries.map(_.values.filter(_.definition == definition).toSeq)

  override protected[datasets] def latest(
    definition: DatasetDefinition.Id,
    devices: Seq[Device.Id],
    until: Option[Instant]
  ): Future[Option[DatasetEntry]] =
    underlying.entries.map { entries =>
      entries.values
        .filter { entry =>
          (
            entry.definition == definition
            && (devices.isEmpty || devices.contains(entry.device))
            && until.forall(entry.created.isBefore)
          )
        }
        .toSeq
        .sortBy(_.created)
        .lastOption
    }

  override def init(): Future[Done] = underlying.init()

  override def drop(): Future[Done] = underlying.drop()
}

object MockDatasetEntryStore {
  def apply()(implicit
    system: ActorSystem[Nothing],
    timeout: Timeout
  ): DatasetEntryStore = {
    implicit val telemetry: TelemetryContext = MockTelemetryContext()

    val underlying = MemoryStore[DatasetEntry.Id, DatasetEntry](
      s"mock-dataset-entry-store-${java.util.UUID.randomUUID()}"
    )

    new MockDatasetEntryStore(underlying)
  }
}
