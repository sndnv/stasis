package stasis.test.specs.unit.server.model.mocks

import akka.Done
import akka.actor.ActorSystem
import akka.util.Timeout
import stasis.core.persistence.backends.memory.MemoryBackend
import stasis.server.model.datasets.{DatasetEntry, DatasetEntryStore}

import scala.concurrent.{ExecutionContext, Future}

class MockDatasetEntryStore()(implicit system: ActorSystem, timeout: Timeout) extends DatasetEntryStore {
  private val backend: MemoryBackend[DatasetEntry.Id, DatasetEntry] =
    MemoryBackend.untyped[DatasetEntry.Id, DatasetEntry](
      s"mock-dataset-entry-store-${java.util.UUID.randomUUID()}"
    )

  override protected implicit def ec: ExecutionContext = system.dispatcher

  override protected def create(entry: DatasetEntry): Future[Done] =
    backend.put(entry.id, entry)

  override protected def delete(entry: DatasetEntry.Id): Future[Boolean] =
    backend.delete(entry)

  override protected def get(entry: DatasetEntry.Id): Future[Option[DatasetEntry]] =
    backend.get(entry)

  override protected def list(definition: DatasetEntry.Id): Future[Map[DatasetEntry.Id, DatasetEntry]] =
    backend.entries.map(_.filter(_._2.definition == definition))
}
