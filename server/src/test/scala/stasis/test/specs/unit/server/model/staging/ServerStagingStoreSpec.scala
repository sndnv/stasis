package stasis.test.specs.unit.server.model.staging

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorSystem, Behavior, SpawnProtocol}
import akka.stream.scaladsl.Source
import akka.util.ByteString
import stasis.core.networking.http.HttpEndpointAddress
import stasis.core.packaging.{Crate, Manifest}
import stasis.core.persistence.crates.CrateStore
import stasis.core.persistence.staging.StagingStore
import stasis.core.routing.{Node, NodeProxy}
import stasis.server.model.staging.ServerStagingStore
import stasis.shared.security.Permission
import stasis.test.specs.unit.AsyncUnitSpec
import stasis.test.specs.unit.core.networking.mocks.{MockGrpcEndpointClient, MockHttpEndpointClient}
import stasis.test.specs.unit.core.persistence.mocks.MockCrateStore

import scala.concurrent.Future
import scala.concurrent.duration._

class ServerStagingStoreSpec extends AsyncUnitSpec {
  "A ServerStagingStore" should "provide a view resource (service)" in {
    val store = ServerStagingStore(
      store = new StagingStore(crateStore = new MockCrateStore(), destagingDelay = 1.second)
    )

    store.view().requiredPermission should be(Permission.View.Service)
  }

  it should "return a list of pending destaging operations via view resource (service)" in {
    val underlying = new StagingStore(crateStore = new MockCrateStore(), destagingDelay = 1.second)
    val store = ServerStagingStore(store = underlying)

    underlying
      .stage(
        manifest = testManifest,
        destinations = destinations,
        content = Source.single(testContent),
        viaProxy = proxy
      )
      .await

    store.view().list().map { result =>
      result.values.toList match {
        case actualDestaging :: Nil => actualDestaging.crate should be(testManifest.crate)
        case other                  => fail(s"Unexpected response received: [$other]")
      }
    }
  }

  it should "provide management resource (service)" in {
    val store = ServerStagingStore(
      store = new StagingStore(crateStore = new MockCrateStore(), destagingDelay = 1.second)
    )

    store.manage().requiredPermission should be(Permission.Manage.Service)
  }

  it should "allow dropping pending destaging operations via management resource (service)" in {
    val underlying = new StagingStore(crateStore = new MockCrateStore(), destagingDelay = 1.second)
    val store = ServerStagingStore(store = underlying)

    underlying
      .stage(
        manifest = testManifest,
        destinations = destinations,
        content = Source.single(testContent),
        viaProxy = proxy
      )
      .await

    for {
      somePendingDestagingOps <- store.view().list()
      dropped <- store.manage().drop(testManifest.crate)
      noPendingDestagingOps <- store.view().list()
    } yield {
      somePendingDestagingOps.size should be(1)
      somePendingDestagingOps.headOption.map(_._1) should be(Some(testManifest.crate))
      dropped should be(true)
      noPendingDestagingOps should be(Map.empty)
    }
  }

  private implicit val system: ActorSystem[SpawnProtocol.Command] = ActorSystem(
    Behaviors.setup(_ => SpawnProtocol()): Behavior[SpawnProtocol.Command],
    "ServerStagingStoreSpec"
  )

  private val testContent = ByteString("some value")

  private val testManifest = Manifest(
    crate = Crate.generateId(),
    size = testContent.size.toLong,
    copies = 4,
    source = Node.generateId(),
    origin = Node.generateId()
  )

  private val destinations: Map[Node, Int] = Map(
    Node.Remote.Http(id = Node.generateId(), address = HttpEndpointAddress("localhost:8000"), storageAllowed = true) -> 1,
    Node.Remote.Http(id = Node.generateId(), address = HttpEndpointAddress("localhost:9000"), storageAllowed = true) -> 1
  )

  private val proxy: NodeProxy = new NodeProxy(
    httpClient = new MockHttpEndpointClient(),
    grpcClient = new MockGrpcEndpointClient()
  ) {
    override protected def crateStore(id: Node.Id, storeDescriptor: CrateStore.Descriptor): Future[CrateStore] =
      Future.failed(new IllegalStateException("Operation not supported"))
  }
}
