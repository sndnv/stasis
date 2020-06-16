package stasis.test.specs.unit.core.persistence.manifests

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorSystem, Behavior, SpawnProtocol}
import stasis.core.packaging.{Crate, Manifest}
import stasis.core.persistence.backends.memory.MemoryBackend
import stasis.core.persistence.manifests.ManifestStore
import stasis.core.routing.Node
import stasis.test.specs.unit.AsyncUnitSpec

class ManifestStoreSpec extends AsyncUnitSpec {
  "A ManifestStore" should "add, retrieve and delete manifests" in {
    val store = createStore()

    val expectedManifest = Manifest(
      crate = Crate.generateId(),
      size = 1,
      copies = 4,
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

  private implicit val system: ActorSystem[SpawnProtocol.Command] = ActorSystem(
    Behaviors.setup(_ => SpawnProtocol()): Behavior[SpawnProtocol.Command],
    "ManifestStoreSpec"
  )

  private def createStore(): ManifestStore =
    ManifestStore(
      MemoryBackend[Crate.Id, Manifest](name = s"manifest-store-${java.util.UUID.randomUUID()}")
    )
}
