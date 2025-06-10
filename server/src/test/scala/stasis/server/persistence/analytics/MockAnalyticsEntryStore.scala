package stasis.server.persistence.analytics

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

import org.apache.pekko.Done
import org.apache.pekko.actor.typed.ActorSystem
import org.apache.pekko.util.Timeout

import stasis.layers.persistence.KeyValueStore
import stasis.layers.persistence.memory.MemoryStore
import stasis.layers.persistence.migration.Migration
import stasis.layers.telemetry.MockTelemetryContext
import stasis.layers.telemetry.TelemetryContext
import stasis.shared.model.analytics.StoredAnalyticsEntry

class MockAnalyticsEntryStore(
  underlying: KeyValueStore[StoredAnalyticsEntry.Id, StoredAnalyticsEntry]
)(implicit system: ActorSystem[Nothing])
    extends AnalyticsEntryStore {
  override protected implicit def ec: ExecutionContext = system.executionContext

  override val name: String = underlying.name()

  override val migrations: Seq[Migration] = Seq.empty

  override protected[analytics] def put(entry: StoredAnalyticsEntry): Future[Done] =
    underlying.put(entry.id, entry)

  override protected[analytics] def delete(entry: StoredAnalyticsEntry.Id): Future[Boolean] =
    underlying.delete(entry)

  override protected[analytics] def get(entry: StoredAnalyticsEntry.Id): Future[Option[StoredAnalyticsEntry]] =
    underlying.get(entry)

  override protected[analytics] def list(): Future[Seq[StoredAnalyticsEntry]] =
    underlying.entries.map(_.values.toSeq)

  override def init(): Future[Done] = underlying.init()

  override def drop(): Future[Done] = underlying.drop()
}

object MockAnalyticsEntryStore {
  def apply()(implicit
    system: ActorSystem[Nothing],
    timeout: Timeout
  ): MockAnalyticsEntryStore = {
    implicit val telemetry: TelemetryContext = MockTelemetryContext()

    val underlying = MemoryStore[StoredAnalyticsEntry.Id, StoredAnalyticsEntry](
      s"mock-analytics-entries-store-${java.util.UUID.randomUUID()}"
    )

    new MockAnalyticsEntryStore(underlying)
  }
}
