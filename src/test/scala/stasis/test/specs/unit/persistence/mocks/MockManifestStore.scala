package stasis.test.specs.unit.persistence.mocks

import akka.Done
import akka.actor.Scheduler
import akka.actor.typed.scaladsl.AskPattern._
import akka.actor.typed.{ActorSystem, SpawnProtocol}
import akka.util.Timeout
import stasis.packaging.{Crate, Manifest}
import stasis.persistence.manifests.ManifestStore

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}

class MockManifestStore(implicit system: ActorSystem[SpawnProtocol]) extends ManifestStore {
  private type StoreKey = Crate.Id
  private type StoreValue = Manifest

  private implicit val timeout: Timeout = 3.seconds
  private implicit val scheduler: Scheduler = system.scheduler
  private implicit val ec: ExecutionContext = system.executionContext

  private val storeRef =
    system ? SpawnProtocol.Spawn(
      MapStoreActor.store(Map.empty[StoreKey, StoreValue]),
      s"mock-manifest-store-${java.util.UUID.randomUUID()}"
    )

  override def put(manifest: Manifest): Future[Done] =
    storeRef.flatMap(_ ? (ref => MapStoreActor.Put(manifest.crate, manifest, ref)))

  override def get(crate: Crate.Id): Future[Option[Manifest]] =
    storeRef.flatMap(_ ? (ref => MapStoreActor.Get(crate, ref)))
}
