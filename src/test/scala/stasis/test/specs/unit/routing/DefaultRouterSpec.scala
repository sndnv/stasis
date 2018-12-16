package stasis.test.specs.unit.routing

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.scaladsl.adapter._
import akka.actor.typed.{ActorSystem, Behavior, SpawnProtocol}
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.Source
import akka.util.{ByteString, Timeout}
import org.scalatest.concurrent.Eventually
import stasis.networking.http.HttpEndpointAddress
import stasis.packaging.{Crate, Manifest}
import stasis.persistence.staging.StagingStore
import stasis.persistence.{CrateStorageRequest, CrateStorageReservation}
import stasis.routing.exceptions.{DistributionFailure, PullFailure, PushFailure}
import stasis.routing.{DefaultRouter, Node}
import stasis.test.specs.unit.AsyncUnitSpec
import stasis.test.specs.unit.networking.mocks.MockEndpointClient
import stasis.test.specs.unit.persistence.mocks.{MockCrateStore, MockManifestStore, MockNodeStore, MockReservationStore}

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

class DefaultRouterSpec extends AsyncUnitSpec with Eventually {

  override implicit val timeout: Timeout = 500.milliseconds

  private implicit val system: ActorSystem[SpawnProtocol] = ActorSystem(
    Behaviors.setup(_ => SpawnProtocol.behavior): Behavior[SpawnProtocol],
    "DefaultRouterSpec"
  )

  private implicit val mat: ActorMaterializer = ActorMaterializer()(system.toUntyped)

  private implicit val ec: ExecutionContext = system.executionContext

  override implicit val patienceConfig: PatienceConfig = PatienceConfig(3.second, 250.milliseconds)

  private trait TestFixtures {
    lazy val reservationStore: MockReservationStore = new MockReservationStore()
    lazy val crateStore: MockCrateStore = new MockCrateStore(reservationStore)
    lazy val manifestStore: MockManifestStore = new MockManifestStore
    lazy val nodeStore: MockNodeStore = new MockNodeStore
    lazy val localNode: Node.Local = Node.Local(Node.generateId(), crateStore = crateStore)
    lazy val stagingStore: Option[StagingStore] = None
    lazy val remoteNodes: Seq[Node.Remote.Http] = Seq(
      Node.Remote.Http(Node.generateId(), address = HttpEndpointAddress("localhost:8000")),
      Node.Remote.Http(Node.generateId(), address = HttpEndpointAddress("localhost:9000"))
    )

    def testNodes: Seq[Node] = Seq(
      remoteNodes.head,
      localNode,
      remoteNodes.last
    )

    Future.sequence(testNodes.map(node => nodeStore.put(node))).await
  }

  private class TestRouter(
    val fixtures: TestFixtures = new TestFixtures {},
    val testClient: MockEndpointClient = new MockEndpointClient()
  )(implicit untypedSystem: akka.actor.ActorSystem = system.toUntyped)
      extends DefaultRouter(
        httpClient = testClient,
        manifestStore = fixtures.manifestStore,
        nodeStore = fixtures.nodeStore.view,
        stagingStore = fixtures.stagingStore
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

  "A DefaultRouter" should "calculate crate copies distribution" in {
    val node1 = Node.Remote.Http(Node.generateId(), address = HttpEndpointAddress("localhost:8000"))
    val node2 = Node.Local(Node.generateId(), crateStore = null)
    val node3 = Node.Remote.Http(Node.generateId(), address = HttpEndpointAddress("localhost:9000"))

    DefaultRouter.distributeCopies(
      availableNodes = Seq(node1),
      sourceNodes = Seq.empty,
      copies = 3
    ) should be(Success(Map(node1 -> 3)))

    DefaultRouter.distributeCopies(
      availableNodes = Seq(node1, node2),
      sourceNodes = Seq.empty,
      copies = 3
    ) should be(Success(Map(node1 -> 1, node2 -> 3)))

    DefaultRouter
      .distributeCopies(
        availableNodes = Seq(node1, node2, node3),
        sourceNodes = Seq.empty,
        copies = 1
      ) should be(Success(Map(node1 -> 1, node2 -> 1, node3 -> 1)))

    DefaultRouter
      .distributeCopies(
        availableNodes = Seq(node1, node2, node3),
        sourceNodes = Seq.empty,
        copies = 5
      ) should be(Success(Map(node1 -> 1, node2 -> 5, node3 -> 1)))

    DefaultRouter
      .distributeCopies(
        availableNodes = Seq(node1, node3),
        sourceNodes = Seq.empty,
        copies = 5
      ) should be(Success(Map(node1 -> 3, node3 -> 2)))

    DefaultRouter.distributeCopies(
      availableNodes = Seq.empty,
      sourceNodes = Seq.empty,
      copies = 2
    ) should be(Failure(DistributionFailure("No nodes provided")))

    DefaultRouter.distributeCopies(
      availableNodes = Seq(node1, node2),
      sourceNodes = Seq.empty,
      copies = 0
    ) should be(Failure(DistributionFailure("No copies requested")))

    DefaultRouter
      .distributeCopies(
        availableNodes = Seq(node1, node2, node3),
        sourceNodes = Seq(node2.id),
        copies = 3
      ) should be(Success(Map(node1 -> 2, node3 -> 1)))

    DefaultRouter
      .distributeCopies(
        availableNodes = Seq(node1, node2, node3),
        sourceNodes = Seq(node2.id),
        copies = 1
      ) should be(Success(Map(node1 -> 1)))

    DefaultRouter
      .distributeCopies(
        availableNodes = Seq(node1, node2, node3),
        sourceNodes = Seq(node1.id, node2.id, node3.id),
        copies = 1
      ) should be(Failure(DistributionFailure("No nodes provided")))
  }

  it should "push data to nodes" in {
    val router = new TestRouter()

    router.push(testManifest, Source.single(testContent)).await

    eventually {
      router.fixtures.crateStore.statistics(MockCrateStore.Statistic.PersistCompleted) should be(1)
      router.fixtures.crateStore.statistics(MockCrateStore.Statistic.PersistFailed) should be(0)
      router.testClient.crateCopies(testManifest.crate).await should be(router.fixtures.remoteNodes.size)
      router.testClient.crateNodes(testManifest.crate).await should be(router.fixtures.remoteNodes.size)
      router.testClient.statistics(MockEndpointClient.Statistic.PushCompleted) should be(
        router.fixtures.remoteNodes.size)
      router.testClient.statistics(MockEndpointClient.Statistic.PushFailed) should be(0)
    }
  }

  it should "push data to nodes using staging" in {
    val stagingCrateStore = new MockCrateStore(new MockReservationStore())
    val testClient = new MockEndpointClient()

    val fixtures = new TestFixtures {
      override lazy val stagingStore: Option[StagingStore] =
        Some(
          new StagingStore(
            crateStore = stagingCrateStore,
            httpClient = testClient,
            destagingDelay = 100.milliseconds
          )(system.toUntyped)
        )
    }

    val router = new TestRouter(fixtures, testClient)

    router.push(testManifest, Source.single(testContent)).await

    stagingCrateStore.statistics(MockCrateStore.Statistic.PersistCompleted) should be(1)
    stagingCrateStore.statistics(MockCrateStore.Statistic.PersistFailed) should be(0)
    stagingCrateStore.statistics(MockCrateStore.Statistic.ReserveCompleted) should be(1)
    stagingCrateStore.statistics(MockCrateStore.Statistic.ReserveLimited) should be(0)
    stagingCrateStore.statistics(MockCrateStore.Statistic.ReserveFailed) should be(0)

    eventually {
      fixtures.crateStore.statistics(MockCrateStore.Statistic.PersistCompleted) should be(1)
      fixtures.crateStore.statistics(MockCrateStore.Statistic.PersistFailed) should be(0)
      testClient.crateCopies(testManifest.crate).await should be(fixtures.remoteNodes.size)
      testClient.crateNodes(testManifest.crate).await should be(fixtures.remoteNodes.size)
      testClient.statistics(MockEndpointClient.Statistic.PushCompleted) should be(fixtures.remoteNodes.size)
      testClient.statistics(MockEndpointClient.Statistic.PushFailed) should be(0)
    }
  }

  it should "fail to push data when no nodes are available" in {
    val router = new TestRouter(
      fixtures = new TestFixtures {
        override def testNodes: Seq[Node] = Seq.empty
      }
    )

    router
      .push(testManifest, Source.single(testContent))
      .map { response =>
        fail(s"Received unexpected push response from router: [$response]")
      }
      .recover {
        case PushFailure(message) =>
          message should be(
            s"Push of crate [${testManifest.crate}] failed: [stasis.routing.exceptions.DistributionFailure: No nodes provided]"
          )
          router.fixtures.crateStore.statistics(MockCrateStore.Statistic.PersistCompleted) should be(0)
          router.fixtures.crateStore.statistics(MockCrateStore.Statistic.PersistFailed) should be(0)
          router.testClient.statistics(MockEndpointClient.Statistic.PushCompleted) should be(0)
          router.testClient.statistics(MockEndpointClient.Statistic.PushFailed) should be(0)
      }
  }

  it should "fail to push data when no copies are requested" in {
    val router = new TestRouter()

    router
      .push(testManifest.copy(copies = 0), Source.single(testContent))
      .map { response =>
        fail(s"Received unexpected push response from router: [$response]")
      }
      .recover {
        case PushFailure(message) =>
          message should be(
            s"Push of crate [${testManifest.crate}] failed: [stasis.routing.exceptions.DistributionFailure: No copies requested]"
          )
          router.fixtures.crateStore.statistics(MockCrateStore.Statistic.PersistCompleted) should be(0)
          router.fixtures.crateStore.statistics(MockCrateStore.Statistic.PersistFailed) should be(0)
          router.testClient.statistics(MockEndpointClient.Statistic.PushCompleted) should be(0)
          router.testClient.statistics(MockEndpointClient.Statistic.PushFailed) should be(0)
      }
  }

  it should "recover from node failure on push" in {
    val fixtures = new TestFixtures {}

    val router = new TestRouter(
      fixtures = fixtures,
      testClient = new MockEndpointClient(
        pushFailureAddresses = Map(
          fixtures.remoteNodes.last.address -> new RuntimeException("test failure")
        )
      )
    )

    router.push(testManifest, Source.single(testContent)).await

    val failedNodesCount = 1

    eventually {
      router.fixtures.crateStore.statistics(MockCrateStore.Statistic.PersistCompleted) should be(1)
      router.fixtures.crateStore.statistics(MockCrateStore.Statistic.PersistFailed) should be(0)
      router.testClient.crateCopies(testManifest.crate).await should be(
        router.fixtures.remoteNodes.size - failedNodesCount
      )
      router.testClient.crateNodes(testManifest.crate).await should be(
        router.fixtures.remoteNodes.size - failedNodesCount
      )
      router.testClient.statistics(MockEndpointClient.Statistic.PushCompleted) should be(
        router.fixtures.remoteNodes.size - failedNodesCount
      )
      router.testClient.statistics(MockEndpointClient.Statistic.PushFailed) should be(failedNodesCount)
    }
  }

  it should "fail to push data when all nodes fail" in {
    val fixtures = new TestFixtures {
      override def testNodes: Seq[Node] = remoteNodes
    }

    val router = new TestRouter(
      fixtures = fixtures,
      testClient = new MockEndpointClient(
        pushFailureAddresses = Map(
          fixtures.remoteNodes.head.address -> new RuntimeException("test failure"),
          fixtures.remoteNodes.last.address -> new RuntimeException("test failure")
        )
      )
    )

    router
      .push(testManifest, Source.single(testContent))
      .map { response =>
        fail(s"Received unexpected push response from router: [$response]")
      }
      .recover {
        case PushFailure(message) =>
          message should be(
            s"Crate [${testManifest.crate}] was not pushed; no content sinks retrieved"
          )

          router.fixtures.crateStore.statistics(MockCrateStore.Statistic.PersistCompleted) should be(0)
          router.fixtures.crateStore.statistics(MockCrateStore.Statistic.PersistFailed) should be(0)
          router.testClient.crateCopies(testManifest.crate).await should be(0)
          router.testClient.crateNodes(testManifest.crate).await should be(0)
          router.testClient.statistics(MockEndpointClient.Statistic.PushCompleted) should be(0)
          router.testClient.statistics(MockEndpointClient.Statistic.PushFailed) should be(fixtures.remoteNodes.size)
      }
  }

  it should "pull data from remote nodes" in {
    val router = new TestRouter(
      fixtures = new TestFixtures {
        override def testNodes: Seq[Node] = remoteNodes
      }
    )

    router.push(testManifest, Source.single(testContent)).await

    eventually {
      router.testClient.statistics(MockEndpointClient.Statistic.PushCompleted) should be(
        router.fixtures.remoteNodes.size
      )
      router.testClient.statistics(MockEndpointClient.Statistic.PushFailed) should be(0)
    }

    val result = router
      .pull(testManifest.crate)
      .flatMap(_.getOrElse(Source.empty).runFold(ByteString.empty)(_ ++ _))
      .await

    result should be(testContent)
    router.fixtures.crateStore.statistics(MockCrateStore.Statistic.PersistCompleted) should be(0)
    router.fixtures.crateStore.statistics(MockCrateStore.Statistic.PersistFailed) should be(0)
    router.fixtures.crateStore.statistics(MockCrateStore.Statistic.RetrieveCompleted) should be(0)
    router.fixtures.crateStore.statistics(MockCrateStore.Statistic.RetrieveEmpty) should be(0)
    router.fixtures.crateStore.statistics(MockCrateStore.Statistic.RetrieveFailed) should be(0)
    router.testClient.statistics(MockEndpointClient.Statistic.PullCompletedWithData) should be(1)
    router.testClient.statistics(MockEndpointClient.Statistic.PullCompletedEmpty) should be(0)
    router.testClient.statistics(MockEndpointClient.Statistic.PullFailed) should be(0)
  }

  it should "pull data from local nodes" in {
    val router = new TestRouter(
      fixtures = new TestFixtures {
        override def testNodes: Seq[Node] = Seq(localNode)
      }
    )

    router.push(testManifest, Source.single(testContent)).await

    eventually {
      router.fixtures.crateStore.statistics(MockCrateStore.Statistic.PersistCompleted) should be(1)
      router.fixtures.crateStore.statistics(MockCrateStore.Statistic.PersistFailed) should be(0)
    }

    val result = router
      .pull(testManifest.crate)
      .flatMap(_.getOrElse(Source.empty).runFold(ByteString.empty)(_ ++ _))
      .await

    result should be(testContent)
    router.fixtures.crateStore.statistics(MockCrateStore.Statistic.RetrieveCompleted) should be(1)
    router.fixtures.crateStore.statistics(MockCrateStore.Statistic.RetrieveEmpty) should be(0)
    router.fixtures.crateStore.statistics(MockCrateStore.Statistic.RetrieveFailed) should be(0)
    router.testClient.statistics(MockEndpointClient.Statistic.PullCompletedWithData) should be(0)
    router.testClient.statistics(MockEndpointClient.Statistic.PullCompletedEmpty) should be(0)
    router.testClient.statistics(MockEndpointClient.Statistic.PullFailed) should be(0)
  }

  it should "fail to pull data when crate manifest is missing" in {
    val router = new TestRouter()

    val missingCrateId = Crate.generateId()

    router
      .pull(missingCrateId)
      .map { response =>
        fail(s"Received unexpected pull response from router: [$response]")
      }
      .recover {
        case PullFailure(message) =>
          message should be(s"Crate [$missingCrateId] was not pulled; failed to retrieve manifest")
          router.fixtures.crateStore.statistics(MockCrateStore.Statistic.RetrieveCompleted) should be(0)
          router.fixtures.crateStore.statistics(MockCrateStore.Statistic.RetrieveEmpty) should be(0)
          router.fixtures.crateStore.statistics(MockCrateStore.Statistic.RetrieveFailed) should be(0)
          router.testClient.statistics(MockEndpointClient.Statistic.PullCompletedWithData) should be(0)
          router.testClient.statistics(MockEndpointClient.Statistic.PullCompletedEmpty) should be(0)
          router.testClient.statistics(MockEndpointClient.Statistic.PullFailed) should be(0)
      }
  }

  it should "fail to pull data if crate manifest has no destinations" in {
    val router = new TestRouter()

    router.fixtures.manifestStore.put(testManifest).await

    router
      .pull(testManifest.crate)
      .map { response =>
        fail(s"Received unexpected pull response from router: [$response]")
      }
      .recover {
        case PullFailure(message) =>
          message should be(s"Crate [${testManifest.crate}] was not pulled; no destinations found")
          router.fixtures.crateStore.statistics(MockCrateStore.Statistic.RetrieveCompleted) should be(0)
          router.fixtures.crateStore.statistics(MockCrateStore.Statistic.RetrieveEmpty) should be(0)
          router.fixtures.crateStore.statistics(MockCrateStore.Statistic.RetrieveFailed) should be(0)
          router.testClient.statistics(MockEndpointClient.Statistic.PullCompletedWithData) should be(0)
          router.testClient.statistics(MockEndpointClient.Statistic.PullCompletedEmpty) should be(0)
          router.testClient.statistics(MockEndpointClient.Statistic.PullFailed) should be(0)
      }
  }

  it should "recover from missing node on pull" in {
    val router = new TestRouter()

    router.push(testManifest, Source.single(testContent)).await

    eventually {
      router.fixtures.crateStore.statistics(MockCrateStore.Statistic.PersistCompleted) should be(1)
      router.fixtures.crateStore.statistics(MockCrateStore.Statistic.PersistFailed) should be(0)
      router.testClient.statistics(MockEndpointClient.Statistic.PushCompleted) should be(
        router.fixtures.remoteNodes.size
      )
      router.testClient.statistics(MockEndpointClient.Statistic.PushFailed) should be(0)
    }

    router.fixtures.manifestStore
      .put(testManifest.copy(destinations = Seq(Node.generateId(), router.fixtures.testNodes.head.id)))
      .await

    val result = router
      .pull(testManifest.crate)
      .flatMap(_.getOrElse(Source.empty).runFold(ByteString.empty)(_ ++ _))
      .await

    result should be(testContent)

    router.fixtures.crateStore.statistics(MockCrateStore.Statistic.RetrieveCompleted) should be(0)
    router.fixtures.crateStore.statistics(MockCrateStore.Statistic.RetrieveEmpty) should be(0)
    router.fixtures.crateStore.statistics(MockCrateStore.Statistic.RetrieveFailed) should be(0)
    router.testClient.statistics(MockEndpointClient.Statistic.PullCompletedWithData) should be(1)
    router.testClient.statistics(MockEndpointClient.Statistic.PullCompletedEmpty) should be(0)
    router.testClient.statistics(MockEndpointClient.Statistic.PullFailed) should be(0)
  }

  it should "recover from content not returned by individual nodes" in {
    val router = new TestRouter(
      fixtures = new TestFixtures {
        override lazy val crateStore: MockCrateStore =
          new MockCrateStore(new MockReservationStore(), retrieveEmpty = true)
      }
    )

    router.push(testManifest, Source.single(testContent)).await

    eventually {
      router.fixtures.crateStore.statistics(MockCrateStore.Statistic.PersistCompleted) should be(1)
      router.fixtures.crateStore.statistics(MockCrateStore.Statistic.PersistFailed) should be(0)
      router.testClient.statistics(MockEndpointClient.Statistic.PushCompleted) should be(
        router.fixtures.remoteNodes.size
      )
      router.testClient.statistics(MockEndpointClient.Statistic.PushFailed) should be(0)
    }

    val result = router
      .pull(testManifest.crate)
      .flatMap(_.getOrElse(Source.empty).runFold(ByteString.empty)(_ ++ _))
      .await

    result should be(testContent)
    router.fixtures.crateStore.statistics(MockCrateStore.Statistic.RetrieveCompleted) should be(0)
    router.fixtures.crateStore.statistics(MockCrateStore.Statistic.RetrieveEmpty) should be(1)
    router.fixtures.crateStore.statistics(MockCrateStore.Statistic.RetrieveFailed) should be(0)
    router.testClient.statistics(MockEndpointClient.Statistic.PullCompletedWithData) should be(1)
    router.testClient.statistics(MockEndpointClient.Statistic.PullCompletedEmpty) should be(0)
    router.testClient.statistics(MockEndpointClient.Statistic.PullFailed) should be(0)
  }

  it should "recover from node failure on pull" in {
    val router = new TestRouter(
      fixtures = new TestFixtures {
        override lazy val crateStore: MockCrateStore =
          new MockCrateStore(new MockReservationStore(), retrieveDisabled = true)
      }
    )

    router.push(testManifest, Source.single(testContent)).await

    eventually {
      router.fixtures.crateStore.statistics(MockCrateStore.Statistic.PersistCompleted) should be(1)
      router.fixtures.crateStore.statistics(MockCrateStore.Statistic.PersistFailed) should be(0)
      router.testClient.statistics(MockEndpointClient.Statistic.PushCompleted) should be(
        router.fixtures.remoteNodes.size
      )
      router.testClient.statistics(MockEndpointClient.Statistic.PushFailed) should be(0)
    }

    val result = router
      .pull(testManifest.crate)
      .flatMap(_.getOrElse(Source.empty).runFold(ByteString.empty)(_ ++ _))
      .await

    result should be(testContent)
    router.fixtures.crateStore.statistics(MockCrateStore.Statistic.RetrieveCompleted) should be(0)
    router.fixtures.crateStore.statistics(MockCrateStore.Statistic.RetrieveEmpty) should be(0)
    router.fixtures.crateStore.statistics(MockCrateStore.Statistic.RetrieveFailed) should be(1)
    router.testClient.statistics(MockEndpointClient.Statistic.PullCompletedWithData) should be(1)
    router.testClient.statistics(MockEndpointClient.Statistic.PullCompletedEmpty) should be(0)
    router.testClient.statistics(MockEndpointClient.Statistic.PullFailed) should be(0)
  }

  it should "fail to pull missing data" in {
    val fixtures = new TestFixtures {
      override def testNodes: Seq[Node] = remoteNodes
    }

    val router = new TestRouter(
      fixtures = fixtures,
      testClient = new MockEndpointClient(
        pullEmptyAddresses = fixtures.remoteNodes.map(_.address)
      )
    )

    router.push(testManifest, Source.single(testContent)).await

    router.pull(testManifest.crate).await should be(None)

    router.fixtures.crateStore.statistics(MockCrateStore.Statistic.PersistCompleted) should be(0)
    router.fixtures.crateStore.statistics(MockCrateStore.Statistic.PersistFailed) should be(0)
    router.fixtures.crateStore.statistics(MockCrateStore.Statistic.RetrieveCompleted) should be(0)
    router.fixtures.crateStore.statistics(MockCrateStore.Statistic.RetrieveEmpty) should be(0)
    router.fixtures.crateStore.statistics(MockCrateStore.Statistic.RetrieveFailed) should be(0)
    router.testClient.statistics(MockEndpointClient.Statistic.PullCompletedWithData) should be(0)
    router.testClient.statistics(MockEndpointClient.Statistic.PullCompletedEmpty) should be(fixtures.testNodes.size)
    router.testClient.statistics(MockEndpointClient.Statistic.PullFailed) should be(0)
  }

  it should "process reservation requests for local nodes" in {
    val router = new TestRouter(
      fixtures = new TestFixtures {
        override def testNodes: Seq[Node] = Seq(localNode)
      }
    )

    val request = CrateStorageRequest(
      size = 1,
      copies = 1,
      retention = 1.second,
      origin = Node.generateId(),
      source = Node.generateId()
    )

    val expectedReservation = CrateStorageReservation(
      id = CrateStorageReservation.generateId(),
      size = request.size,
      copies = request.copies,
      retention = request.retention,
      expiration = 1.day,
      origin = Node.generateId()
    )

    for {
      response <- router.reserve(request)
    } yield {
      response should be(defined)

      val actualReservation = response.get
      actualReservation.size should be(expectedReservation.size)
      actualReservation.copies should be(expectedReservation.copies)
      actualReservation.retention should be(expectedReservation.retention)
      actualReservation.expiration should be(expectedReservation.expiration)
    }
  }

  it should "not process reservation requests for remote nodes" in {
    val router = new TestRouter(
      fixtures = new TestFixtures {
        override def testNodes: Seq[Node] = remoteNodes
      }
    )

    val request = CrateStorageRequest(
      size = 1,
      copies = 1,
      retention = 1.second,
      origin = Node.generateId(),
      source = Node.generateId()
    )

    router.reserve(request).map(_ should be(None))
  }

  it should "fail to process reservation requests when no nodes are available" in {
    val router = new TestRouter(
      fixtures = new TestFixtures {
        override def testNodes: Seq[Node] = Seq.empty
      }
    )

    val request = CrateStorageRequest(
      size = 1,
      copies = 1,
      retention = 1.second,
      origin = Node.generateId(),
      source = Node.generateId()
    )

    router.reserve(request).map(_ should be(None))
  }

  it should "recover from nodes failing to process reservation requests" in {
    val router = new TestRouter(
      fixtures = new TestFixtures {
        override lazy val crateStore: MockCrateStore =
          new MockCrateStore(new MockReservationStore(), reservationDisabled = true)
      }
    )

    val request = CrateStorageRequest(
      size = 1,
      copies = 1,
      retention = 1.second,
      origin = Node.generateId(),
      source = Node.generateId()
    )

    router.reserve(request).map(_ should be(None))
  }
}
