package stasis.test.specs.unit.persistence.mocks

import akka.Done
import akka.actor.typed.{ActorSystem, SpawnProtocol}
import akka.util.Timeout
import stasis.packaging.{Crate, Manifest}
import stasis.persistence.backends.memory.MemoryBackend
import stasis.persistence.manifests.ManifestStore

import scala.concurrent.Future
import scala.concurrent.duration._

class MockManifestStore(implicit system: ActorSystem[SpawnProtocol]) extends ManifestStore {
  private type StoreKey = Crate.Id
  private type StoreValue = Manifest

  private implicit val timeout: Timeout = 3.seconds

  private val store =
    MemoryBackend.typed[StoreKey, StoreValue](name = s"mock-manifest-store-${java.util.UUID.randomUUID()}")

  override def put(manifest: Manifest): Future[Done] = store.put(manifest.crate, manifest)
  override def delete(crate: Crate.Id): Future[Boolean] = store.delete(crate)
  override def get(crate: Crate.Id): Future[Option[Manifest]] = store.get(crate)
}
