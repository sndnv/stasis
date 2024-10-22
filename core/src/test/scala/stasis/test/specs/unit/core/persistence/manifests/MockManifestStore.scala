package stasis.test.specs.unit.core.persistence.manifests

import scala.concurrent.Future
import scala.concurrent.duration._

import org.apache.pekko.Done
import org.apache.pekko.actor.typed.ActorSystem
import org.apache.pekko.util.Timeout

import stasis.core.packaging.Crate
import stasis.core.packaging.Manifest
import stasis.core.persistence.manifests.ManifestStore
import stasis.layers.persistence.KeyValueStore
import stasis.layers.persistence.memory.MemoryStore
import stasis.layers.persistence.migration.Migration
import stasis.layers.telemetry.MockTelemetryContext
import stasis.layers.telemetry.TelemetryContext

class MockManifestStore(
  underlying: KeyValueStore[Crate.Id, Manifest]
)(implicit system: ActorSystem[Nothing])
    extends ManifestStore {
  import system.executionContext

  override val name: String = underlying.name()

  override val migrations: Seq[Migration] = Seq.empty

  override def init(): Future[Done] = underlying.init()

  override def drop(): Future[Done] = underlying.drop()

  override def put(manifest: Manifest): Future[Done] = underlying.put(manifest.crate, manifest)

  override def delete(crate: Crate.Id): Future[Boolean] = underlying.delete(crate)

  override def get(crate: Crate.Id): Future[Option[Manifest]] = underlying.get(crate)

  override def list(): Future[Seq[Manifest]] = underlying.entries.map(_.values.toSeq)
}

object MockManifestStore {
  def apply()(implicit system: ActorSystem[Nothing]): MockManifestStore = {
    implicit val telemetry: TelemetryContext = MockTelemetryContext()

    implicit val timeout: Timeout = 3.seconds

    val underlying = MemoryStore[Crate.Id, Manifest](name = s"mock-manifest-store-${java.util.UUID.randomUUID()}")

    new MockManifestStore(underlying = underlying)
  }
}
