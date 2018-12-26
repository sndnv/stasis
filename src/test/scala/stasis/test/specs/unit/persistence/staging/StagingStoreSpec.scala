package stasis.test.specs.unit.persistence.staging

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorSystem, Behavior, SpawnProtocol}
import akka.stream.scaladsl.Source
import akka.util.ByteString
import org.scalatest.concurrent.Eventually
import stasis.networking.http.HttpEndpointAddress
import stasis.packaging.{Crate, Manifest}
import stasis.persistence.staging.StagingStore
import stasis.routing.Node
import stasis.test.specs.unit.AsyncUnitSpec
import stasis.test.specs.unit.networking.mocks.MockEndpointClient
import stasis.test.specs.unit.persistence.mocks.{MockCrateStore, MockReservationStore}

import scala.concurrent.duration._
import scala.util.control.NonFatal

class StagingStoreSpec extends AsyncUnitSpec with Eventually {

  private implicit val system: ActorSystem[SpawnProtocol] = ActorSystem(
    Behaviors.setup(_ => SpawnProtocol.behavior): Behavior[SpawnProtocol],
    "StagingStoreSpec"
  )

  override implicit val patienceConfig: PatienceConfig = PatienceConfig(3.second, 250.milliseconds)

  private trait TestFixtures {
    lazy val stagingCrateStore: MockCrateStore = new MockCrateStore(new MockReservationStore())
    lazy val nodeCrateStore: MockCrateStore = new MockCrateStore(new MockReservationStore())
    lazy val testClient: MockEndpointClient = new MockEndpointClient()
    lazy val localNodes: Map[Node.Local, Int] = Map(
      Node.Local(Node.generateId(), crateStore = nodeCrateStore) -> 1
    )
    lazy val remoteNodes: Map[Node.Remote.Http, Int] = Map(
      Node.Remote.Http(Node.generateId(), address = HttpEndpointAddress("localhost:8000")) -> 1,
      Node.Remote.Http(Node.generateId(), address = HttpEndpointAddress("localhost:9000")) -> 1
    )
  }

  private class TestStagingStore(
    val fixtures: TestFixtures = new TestFixtures {}
  ) extends StagingStore(
        fixtures.stagingCrateStore,
        fixtures.testClient,
        destagingDelay = 50.milliseconds
      )

  private val testContent = ByteString("some value")

  private val testManifest = Manifest(
    crate = Crate.generateId(),
    size = testContent.size,
    copies = 4,
    retention = 60.seconds,
    source = Node.generateId(),
    origin = Node.generateId()
  )

  "A StagingStore" should "stage crates to temporary storage" in {
    val fixtures = new TestFixtures {}
    val store = new TestStagingStore(fixtures)
    val destinations: Map[Node, Int] = fixtures.remoteNodes ++ fixtures.localNodes

    store.stage(testManifest, destinations = destinations, content = Source.single(testContent)).map { _ =>
      fixtures.stagingCrateStore.statistics(MockCrateStore.Statistic.PersistCompleted) should be(1)
      fixtures.stagingCrateStore.statistics(MockCrateStore.Statistic.PersistFailed) should be(0)
      fixtures.stagingCrateStore.statistics(MockCrateStore.Statistic.ReserveCompleted) should be(1)
      fixtures.stagingCrateStore.statistics(MockCrateStore.Statistic.ReserveLimited) should be(0)
      fixtures.stagingCrateStore.statistics(MockCrateStore.Statistic.ReserveFailed) should be(0)
    }
  }

  it should "destage crates from temporary storage to multiple crate destinations" in {
    val fixtures = new TestFixtures {}
    val store = new TestStagingStore(fixtures)
    val destinations: Map[Node, Int] = fixtures.remoteNodes ++ fixtures.localNodes

    store.stage(testManifest, destinations = destinations, content = Source.single(testContent)).map { _ =>
      eventually {
        fixtures.stagingCrateStore.statistics(MockCrateStore.Statistic.RetrieveCompleted) should be(1)
        fixtures.stagingCrateStore.statistics(MockCrateStore.Statistic.RetrieveEmpty) should be(0)
        fixtures.stagingCrateStore.statistics(MockCrateStore.Statistic.RetrieveFailed) should be(0)

        fixtures.nodeCrateStore.statistics(MockCrateStore.Statistic.PersistCompleted) should be(1)
        fixtures.nodeCrateStore.statistics(MockCrateStore.Statistic.PersistFailed) should be(0)

        fixtures.testClient.crateCopies(testManifest.crate).await should be(fixtures.remoteNodes.size)
        fixtures.testClient.crateNodes(testManifest.crate).await should be(fixtures.remoteNodes.size)
        fixtures.testClient.statistics(MockEndpointClient.Statistic.PushCompleted) should be(fixtures.remoteNodes.size)
        fixtures.testClient.statistics(MockEndpointClient.Statistic.PushFailed) should be(0)
      }
    }
  }

  it should "destage crates from temporary storage to a single crate destination" in {
    val fixtures = new TestFixtures {}
    val store = new TestStagingStore(fixtures)
    val destinations: Map[Node, Int] = fixtures.localNodes.map { case (k, v) => (k: Node, v) }

    store.stage(testManifest, destinations = destinations, content = Source.single(testContent)).map { _ =>
      eventually {
        fixtures.stagingCrateStore.statistics(MockCrateStore.Statistic.RetrieveCompleted) should be(1)
        fixtures.stagingCrateStore.statistics(MockCrateStore.Statistic.RetrieveEmpty) should be(0)
        fixtures.stagingCrateStore.statistics(MockCrateStore.Statistic.RetrieveFailed) should be(0)

        fixtures.nodeCrateStore.statistics(MockCrateStore.Statistic.PersistCompleted) should be(1)
        fixtures.nodeCrateStore.statistics(MockCrateStore.Statistic.PersistFailed) should be(0)
      }
    }
  }

  it should "fail to stage crates if no destinations are set" in {
    val fixtures = new TestFixtures {}
    val store = new TestStagingStore(fixtures)
    val destinations: Map[Node, Int] = Map.empty

    store
      .stage(testManifest, destinations = destinations, content = Source.single(testContent))
      .map { response =>
        fail(s"Received unexpected response from store: [$response]")
      }
      .recover {
        case NonFatal(e) =>
          e.getMessage should be(
            s"Failed to stage crate [${testManifest.crate}]; no destinations specified"
          )
      }
  }

  it should "fail to stage crates if storage cannot be reserved staging crate store" in {
    val fixtures = new TestFixtures {
      override lazy val stagingCrateStore: MockCrateStore = new MockCrateStore(
        reservationStore = new MockReservationStore(),
        maxReservationSize = Some(1)
      )
    }
    val store = new TestStagingStore(fixtures)
    val destinations: Map[Node, Int] = fixtures.remoteNodes ++ fixtures.localNodes

    store
      .stage(testManifest, destinations = destinations, content = Source.single(testContent))
      .map { response =>
        fail(s"Received unexpected response from store: [$response]")
      }
      .recover {
        case NonFatal(e) =>
          e.getMessage should be(
            s"Failed to stage crate [${testManifest.crate}]; staging crate store reservation failed"
          )
      }
  }

  it should "fail to stage crates if crate content is missing" in {
    {
      val fixtures = new TestFixtures {
        override lazy val stagingCrateStore: MockCrateStore =
          new MockCrateStore(new MockReservationStore(), retrieveEmpty = true)
      }
      val store = new TestStagingStore(fixtures)
      val destinations: Map[Node, Int] = fixtures.remoteNodes ++ fixtures.localNodes

      store.stage(testManifest, destinations = destinations, content = Source.single(testContent)).map { _ =>
        eventually {
          fixtures.stagingCrateStore.statistics(MockCrateStore.Statistic.RetrieveCompleted) should be(0)
          fixtures.stagingCrateStore.statistics(MockCrateStore.Statistic.RetrieveEmpty) should be(1)
          fixtures.stagingCrateStore.statistics(MockCrateStore.Statistic.RetrieveFailed) should be(0)

          fixtures.nodeCrateStore.statistics(MockCrateStore.Statistic.PersistCompleted) should be(0)
          fixtures.nodeCrateStore.statistics(MockCrateStore.Statistic.PersistFailed) should be(0)
        }
      }
    }
  }

  it should "successfully drop scheduled crate destage operations" in {
    val fixtures = new TestFixtures {}
    val store = new TestStagingStore(fixtures)
    val destinations: Map[Node, Int] = fixtures.remoteNodes ++ fixtures.localNodes

    for {
      _ <- store.stage(testManifest, destinations = destinations, content = Source.single(testContent))
      result <- store.drop(testManifest.crate)
    } yield {
      fixtures.stagingCrateStore.statistics(MockCrateStore.Statistic.PersistCompleted) should be(1)
      fixtures.stagingCrateStore.statistics(MockCrateStore.Statistic.PersistFailed) should be(0)
      fixtures.stagingCrateStore.statistics(MockCrateStore.Statistic.ReserveCompleted) should be(1)
      fixtures.stagingCrateStore.statistics(MockCrateStore.Statistic.ReserveLimited) should be(0)
      fixtures.stagingCrateStore.statistics(MockCrateStore.Statistic.ReserveFailed) should be(0)

      result should be(true)
    }
  }

  it should "fail to drop missing crate destage operations" in {
    val fixtures = new TestFixtures {}
    val store = new TestStagingStore(fixtures)

    for {
      result <- store.drop(testManifest.crate)
    } yield {
      fixtures.stagingCrateStore.statistics(MockCrateStore.Statistic.PersistCompleted) should be(0)
      fixtures.stagingCrateStore.statistics(MockCrateStore.Statistic.PersistFailed) should be(0)
      fixtures.stagingCrateStore.statistics(MockCrateStore.Statistic.ReserveCompleted) should be(0)
      fixtures.stagingCrateStore.statistics(MockCrateStore.Statistic.ReserveLimited) should be(0)
      fixtures.stagingCrateStore.statistics(MockCrateStore.Statistic.ReserveFailed) should be(0)

      result should be(false)
    }
  }
}