package stasis.test.specs.unit.persistence.manifests

import scala.concurrent.duration._

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorSystem, Behavior, SpawnProtocol}
import stasis.packaging.{Crate, Manifest}
import stasis.persistence.backends.memory.MemoryBackend
import stasis.persistence.manifests.ManifestStore
import stasis.routing.Node
import stasis.test.specs.unit.AsyncUnitSpec

class ManifestStoreSpec extends AsyncUnitSpec {

  private implicit val system: ActorSystem[SpawnProtocol] = ActorSystem(
    Behaviors.setup(_ => SpawnProtocol.behavior): Behavior[SpawnProtocol],
    "ManifestStoreSpec"
  )

  private def createStore(): ManifestStore = ManifestStore(
    MemoryBackend[Crate.Id, Manifest](name = s"manifest-store-${java.util.UUID.randomUUID()}")
  )

  "A ManifestStore" should "add, retrieve and delete manifests" in {
    val store = createStore()

    val expectedManifest = Manifest(
      crate = Crate.generateId(),
      size = 1,
      copies = 4,
      retention = 60.seconds,
      source = Node.generateId(),
      origin = Node.generateId()
    )

    for {
      _ <- store.put(expectedManifest)
      actualManifest <- store.get(expectedManifest.crate)
      _ <- store.delete(expectedManifest.crate)
      missingManifest <- store.get(expectedManifest.crate)
    } yield {
      actualManifest should be(Some(expectedManifest))
      missingManifest should be(None)
    }
  }

  it should "provide a read-only view" in {
    val store = createStore()
    val storeView = store.view

    val expectedManifest = Manifest(
      crate = Crate.generateId(),
      size = 1,
      copies = 4,
      retention = 60.seconds,
      source = Node.generateId(),
      origin = Node.generateId()
    )

    for {
      _ <- store.put(expectedManifest)
      actualManifest <- storeView.get(expectedManifest.crate)
      _ <- store.delete(expectedManifest.crate)
      missingManifest <- storeView.get(expectedManifest.crate)
    } yield {
      actualManifest should be(Some(expectedManifest))
      missingManifest should be(None)
      a[ClassCastException] should be thrownBy { val _ = storeView.asInstanceOf[ManifestStore] }
    }
  }
}
