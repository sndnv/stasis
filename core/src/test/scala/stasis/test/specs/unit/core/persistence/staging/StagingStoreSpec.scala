package stasis.test.specs.unit.core.persistence.staging

import java.time.Instant

import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.control.NonFatal

import org.apache.pekko.actor.typed.ActorSystem
import org.apache.pekko.actor.typed.scaladsl.Behaviors
import org.apache.pekko.stream.scaladsl.Source
import org.apache.pekko.util.ByteString
import org.scalatest.concurrent.Eventually
import org.scalatest.Assertion
import org.scalatest.BeforeAndAfterAll

import stasis.core.networking.http.HttpEndpointAddress
import stasis.core.packaging.Crate
import stasis.core.packaging.Manifest
import stasis.core.persistence.crates.CrateStore
import stasis.core.persistence.staging.StagingStore
import stasis.core.routing.Node
import stasis.core.routing.NodeProxy
import stasis.layers.telemetry.TelemetryContext
import stasis.test.specs.unit.AsyncUnitSpec
import stasis.test.specs.unit.core.networking.mocks.MockGrpcEndpointClient
import stasis.test.specs.unit.core.networking.mocks.MockHttpEndpointClient
import stasis.test.specs.unit.core.persistence.crates.MockCrateStore
import stasis.test.specs.unit.core.telemetry.MockTelemetryContext

class StagingStoreSpec extends AsyncUnitSpec with Eventually with BeforeAndAfterAll {
  "A StagingStore" should "stage crates to temporary storage" in {
    val store = createTestStagingStore()
    val destinations: Map[Node, Int] = (store.fixtures.remoteNodes ++ store.fixtures.localNodes).toMap

    store
      .stage(
        manifest = testManifest,
        destinations = destinations,
        content = Source.single(testContent),
        viaProxy = store.fixtures.proxy
      )
      .map { _ =>
        store.fixtures.stagingCrateStore.statistics(MockCrateStore.Statistic.PersistCompleted) should be(1)
        store.fixtures.stagingCrateStore.statistics(MockCrateStore.Statistic.PersistFailed) should be(0)
      }
  }

  it should "destage crates from temporary storage to multiple crate destinations" in {
    val store = createTestStagingStore()
    val destinations: Map[Node, Int] = (store.fixtures.remoteNodes ++ store.fixtures.localNodes).toMap

    store
      .stage(
        manifest = testManifest,
        destinations = destinations,
        content = Source.single(testContent),
        viaProxy = store.fixtures.proxy
      )
      .map { _ =>
        eventually[Assertion] {
          store.fixtures.stagingCrateStore.statistics(MockCrateStore.Statistic.RetrieveCompleted) should be(1)
          store.fixtures.stagingCrateStore.statistics(MockCrateStore.Statistic.RetrieveEmpty) should be(0)
          store.fixtures.stagingCrateStore.statistics(MockCrateStore.Statistic.RetrieveFailed) should be(0)

          store.fixtures.nodeCrateStore.statistics(MockCrateStore.Statistic.PersistCompleted) should be(1)
          store.fixtures.nodeCrateStore.statistics(MockCrateStore.Statistic.PersistFailed) should be(0)

          store.fixtures.testClient.crateCopies(testManifest.crate).await should be(store.fixtures.remoteNodes.size)
          store.fixtures.testClient.crateNodes(testManifest.crate).await should be(store.fixtures.remoteNodes.size)
          store.fixtures.testClient.statistics(MockHttpEndpointClient.Statistic.PushCompleted) should be(
            store.fixtures.remoteNodes.size
          )
          store.fixtures.testClient.statistics(MockHttpEndpointClient.Statistic.PushFailed) should be(0)
        }
      }
  }

  it should "destage crates from temporary storage to a single crate destination" in {
    val store = createTestStagingStore()
    val destinations: Map[Node, Int] = store.fixtures.localNodes.map { case (k, v) => (k: Node, v) }

    store
      .stage(
        manifest = testManifest,
        destinations = destinations,
        content = Source.single(testContent),
        viaProxy = store.fixtures.proxy
      )
      .map { _ =>
        eventually[Assertion] {
          store.fixtures.stagingCrateStore.statistics(MockCrateStore.Statistic.RetrieveCompleted) should be(1)
          store.fixtures.stagingCrateStore.statistics(MockCrateStore.Statistic.RetrieveEmpty) should be(0)
          store.fixtures.stagingCrateStore.statistics(MockCrateStore.Statistic.RetrieveFailed) should be(0)

          store.fixtures.nodeCrateStore.statistics(MockCrateStore.Statistic.PersistCompleted) should be(1)
          store.fixtures.nodeCrateStore.statistics(MockCrateStore.Statistic.PersistFailed) should be(0)
        }
      }
  }

  it should "fail to stage crates if no destinations are set" in {
    val store = createTestStagingStore()
    val destinations: Map[Node, Int] = Map.empty

    store
      .stage(
        manifest = testManifest,
        destinations = destinations,
        content = Source.single(testContent),
        viaProxy = store.fixtures.proxy
      )
      .map { response =>
        fail(s"Received unexpected response from store: [$response]")
      }
      .recover { case NonFatal(e) =>
        e.getMessage should be(
          s"Failed to stage crate [${testManifest.crate}]; no destinations specified"
        )
      }
  }

  it should "fail to stage crates if storage cannot be reserved staging crate store" in {
    implicit val telemetry: MockTelemetryContext = MockTelemetryContext()

    val store = createTestStagingStore(
      Some(
        new TestFixtures {
          override lazy val stagingCrateStore: MockCrateStore = new MockCrateStore(
            maxStorageSize = Some(1)
          )
        }
      )
    )

    val destinations: Map[Node, Int] = (store.fixtures.remoteNodes ++ store.fixtures.localNodes).toMap

    store
      .stage(
        manifest = testManifest,
        destinations = destinations,
        content = Source.single(testContent),
        viaProxy = store.fixtures.proxy
      )
      .map { response =>
        fail(s"Received unexpected response from store: [$response]")
      }
      .recover { case NonFatal(e) =>
        e.getMessage should be(
          s"Failed to stage crate [${testManifest.crate}]; storage not available"
        )
      }
  }

  it should "fail to stage crates if crate content is missing" in {
    implicit val telemetry: MockTelemetryContext = MockTelemetryContext()

    val store = createTestStagingStore(
      Some(
        new TestFixtures {
          override lazy val stagingCrateStore: MockCrateStore =
            new MockCrateStore(retrieveEmpty = true)
        }
      )
    )
    val destinations: Map[Node, Int] = (store.fixtures.remoteNodes ++ store.fixtures.localNodes).toMap

    store
      .stage(
        manifest = testManifest,
        destinations = destinations,
        content = Source.single(testContent),
        viaProxy = store.fixtures.proxy
      )
      .map { _ =>
        eventually[Assertion] {
          store.fixtures.stagingCrateStore.statistics(MockCrateStore.Statistic.RetrieveCompleted) should be(0)
          store.fixtures.stagingCrateStore.statistics(MockCrateStore.Statistic.RetrieveEmpty) should be(1)
          store.fixtures.stagingCrateStore.statistics(MockCrateStore.Statistic.RetrieveFailed) should be(0)

          store.fixtures.nodeCrateStore.statistics(MockCrateStore.Statistic.PersistCompleted) should be(0)
          store.fixtures.nodeCrateStore.statistics(MockCrateStore.Statistic.PersistFailed) should be(0)
        }
      }
  }

  it should "successfully drop scheduled crate destage operations" in {
    val store = createTestStagingStore()
    val destinations: Map[Node, Int] = (store.fixtures.remoteNodes ++ store.fixtures.localNodes).toMap

    for {
      _ <- store.stage(
        testManifest,
        destinations = destinations,
        content = Source.single(testContent),
        viaProxy = store.fixtures.proxy
      )
      result <- store.drop(testManifest.crate)
    } yield {
      store.fixtures.stagingCrateStore.statistics(MockCrateStore.Statistic.PersistCompleted) should be(1)
      store.fixtures.stagingCrateStore.statistics(MockCrateStore.Statistic.PersistFailed) should be(0)

      result should be(true)
    }
  }

  it should "fail to drop missing crate destage operations" in {
    val store = createTestStagingStore()

    for {
      result <- store.drop(testManifest.crate)
    } yield {
      store.fixtures.stagingCrateStore.statistics(MockCrateStore.Statistic.PersistCompleted) should be(0)
      store.fixtures.stagingCrateStore.statistics(MockCrateStore.Statistic.PersistFailed) should be(0)

      result should be(false)
    }
  }

  it should "provide a list of currently pending destaging operations" in {
    val store = createTestStagingStore()
    val destinations: Map[Node, Int] = (store.fixtures.remoteNodes ++ store.fixtures.localNodes).toMap

    store
      .stage(
        manifest = testManifest,
        destinations = destinations,
        content = Source.single(testContent),
        viaProxy = store.fixtures.proxy
      )
      .await

    store.fixtures.stagingCrateStore.statistics(MockCrateStore.Statistic.PersistCompleted) should be(1)
    store.fixtures.stagingCrateStore.statistics(MockCrateStore.Statistic.PersistFailed) should be(0)

    store.pending.map { result =>
      result.size should be(1)
      result.contains(testManifest.crate) should be(true)
    }
  }

  private implicit val system: ActorSystem[Nothing] = ActorSystem(
    Behaviors.ignore,
    "StagingStoreSpec"
  )

  override implicit val patienceConfig: PatienceConfig = PatienceConfig(5.seconds, 250.milliseconds)

  private class TestFixtures()(implicit telemetry: TelemetryContext) {
    lazy val stagingCrateStore: MockCrateStore = new MockCrateStore()
    lazy val nodeCrateStore: MockCrateStore = new MockCrateStore()
    lazy val testClient: MockHttpEndpointClient = new MockHttpEndpointClient()
    lazy val localNodes: Map[Node.Local, Int] = Map(
      Node.Local(
        id = Node.generateId(),
        storeDescriptor = null, /* mock crate store is always provided in this test */
        created = Instant.now(),
        updated = Instant.now()
      ) -> 1
    )
    lazy val remoteNodes: Map[Node.Remote.Http, Int] = Map(
      Node.Remote.Http(
        Node.generateId(),
        address = HttpEndpointAddress("localhost:8000"),
        storageAllowed = true,
        created = Instant.now(),
        updated = Instant.now()
      ) -> 1,
      Node.Remote.Http(
        Node.generateId(),
        address = HttpEndpointAddress("localhost:9000"),
        storageAllowed = true,
        created = Instant.now(),
        updated = Instant.now()
      ) -> 1
    )
    lazy val proxy: NodeProxy = new NodeProxy(
      httpClient = testClient,
      grpcClient = new MockGrpcEndpointClient()
    ) {
      override protected def crateStore(id: Node.Id, storeDescriptor: CrateStore.Descriptor): Future[CrateStore] =
        Future.successful(nodeCrateStore)
    }
  }

  private def createTestStagingStore(
    fixtures: Option[TestFixtures] = None
  ): TestStagingStore = {
    implicit val telemetry: MockTelemetryContext = MockTelemetryContext()
    new TestStagingStore(fixtures.getOrElse(new TestFixtures()))
  }

  private class TestStagingStore(
    val fixtures: TestFixtures
  )(implicit telemetry: TelemetryContext)
      extends StagingStore(
        crateStore = fixtures.stagingCrateStore,
        destagingDelay = 50.milliseconds
      )

  private val testContent = ByteString("some value")

  private val testManifest = Manifest(
    crate = Crate.generateId(),
    size = testContent.size.toLong,
    copies = 4,
    source = Node.generateId(),
    origin = Node.generateId()
  )

  override protected def afterAll(): Unit =
    system.terminate()
}
