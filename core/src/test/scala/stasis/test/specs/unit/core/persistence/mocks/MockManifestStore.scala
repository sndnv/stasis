package stasis.test.specs.unit.core.persistence.mocks

import scala.concurrent.Future
import scala.concurrent.duration._

import org.apache.pekko.Done
import org.apache.pekko.actor.typed.ActorSystem
import org.apache.pekko.util.Timeout

import stasis.core.packaging.Crate
import stasis.core.packaging.Manifest
import stasis.core.persistence.manifests.ManifestStore
import stasis.layers.persistence.memory.MemoryStore
import stasis.layers.telemetry.TelemetryContext

class MockManifestStore(implicit system: ActorSystem[Nothing], telemetry: TelemetryContext) extends ManifestStore {
  private type StoreKey = Crate.Id
  private type StoreValue = Manifest

  private implicit val timeout: Timeout = 3.seconds

  private val store = MemoryStore[StoreKey, StoreValue](name = s"mock-manifest-store-${java.util.UUID.randomUUID()}")

  override def put(manifest: Manifest): Future[Done] = store.put(manifest.crate, manifest)
  override def delete(crate: Crate.Id): Future[Boolean] = store.delete(crate)
  override def get(crate: Crate.Id): Future[Option[Manifest]] = store.get(crate)

  def manifests: Future[Map[StoreKey, StoreValue]] = store.entries
}
