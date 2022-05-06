package stasis.test.specs.unit.core.routing

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorSystem, Behavior, SpawnProtocol}
import akka.stream.scaladsl.Source
import akka.util.ByteString
import org.scalatest.Assertion
import org.scalatest.concurrent.Eventually
import stasis.core.networking.http.HttpEndpointAddress
import stasis.core.packaging.{Crate, Manifest}
import stasis.core.persistence.crates.CrateStore
import stasis.core.persistence.staging.StagingStore
import stasis.core.persistence.{CrateStorageRequest, CrateStorageReservation}
import stasis.core.routing.exceptions.{DiscardFailure, DistributionFailure, PullFailure, PushFailure}
import stasis.core.routing.{DefaultRouter, Node, NodeProxy}
import stasis.test.specs.unit.AsyncUnitSpec
import stasis.test.specs.unit.core.networking.mocks.{MockGrpcEndpointClient, MockHttpEndpointClient}
import stasis.test.specs.unit.core.persistence.mocks._

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

class DefaultRouterSpec extends AsyncUnitSpec with Eventually {
  "A DefaultRouter" should "calculate crate copies distribution" in {
    val node1 = Node.Remote.Http(
      id = Node.generateId(),
      address = HttpEndpointAddress("localhost:8000"),
      storageAllowed = true
    )

    val node2 = Node.Local(
      id = Node.generateId(),
      storeDescriptor = null /* actual crate store is not needed for this test */
    )

    val node3 = Node.Remote.Http(
      id = Node.generateId(),
      address = HttpEndpointAddress("localhost:9000"),
      storageAllowed = true
    )

    val node4 = Node.Remote.Http(
      id = Node.generateId(),
      address = HttpEndpointAddress("localhost:10000"),
      storageAllowed = false
    )

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
        availableNodes = Seq(node1, node2, node3, node4),
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
        availableNodes = Seq(node1, node2, node3, node4),
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

    DefaultRouter
      .distributeCopies(
        availableNodes = Seq(node1, node2, node3, node4),
        sourceNodes = Seq(node1.id, node2.id, node3.id),
        copies = 1
      ) should be(Failure(DistributionFailure("No nodes provided")))
  }

  it should "push crates to nodes" in {
    val router = new TestRouter()

    router.push(testManifest, Source.single(testContent)).await

    eventually[Assertion] {
      router.fixtures.crateStore.statistics(MockCrateStore.Statistic.PersistCompleted) should be(1)
      router.fixtures.crateStore.statistics(MockCrateStore.Statistic.PersistFailed) should be(0)
      router.testClient.crateCopies(testManifest.crate).await should be(router.fixtures.remoteNodes.size)
      router.testClient.crateNodes(testManifest.crate).await should be(router.fixtures.remoteNodes.size)
      router.testClient.statistics(MockHttpEndpointClient.Statistic.PushCompleted) should be(router.fixtures.remoteNodes.size)
      router.testClient.statistics(MockHttpEndpointClient.Statistic.PushFailed) should be(0)
    }
  }

  it should "push crates to nodes using staging" in {
    val stagingCrateStore = new MockCrateStore()
    val testClient = new MockHttpEndpointClient()

    val fixtures = new TestFixtures { parent =>
      override lazy val stagingStore: Option[StagingStore] =
        Some(new StagingStore(crateStore = stagingCrateStore, destagingDelay = 100.milliseconds))
    }

    val router = new TestRouter(fixtures, testClient)

    router.push(testManifest, Source.single(testContent)).await

    stagingCrateStore.statistics(MockCrateStore.Statistic.PersistCompleted) should be(1)
    stagingCrateStore.statistics(MockCrateStore.Statistic.PersistFailed) should be(0)

    eventually[Assertion] {
      fixtures.crateStore.statistics(MockCrateStore.Statistic.PersistCompleted) should be(1)
      fixtures.crateStore.statistics(MockCrateStore.Statistic.PersistFailed) should be(0)
      testClient.crateCopies(testManifest.crate).await should be(fixtures.remoteNodes.size)
      testClient.crateNodes(testManifest.crate).await should be(fixtures.remoteNodes.size)
      testClient.statistics(MockHttpEndpointClient.Statistic.PushCompleted) should be(fixtures.remoteNodes.size)
      testClient.statistics(MockHttpEndpointClient.Statistic.PushFailed) should be(0)
    }
  }

  it should "expire reservations on successful push" in {
    val testReservationStore = new MockReservationStore(ignoreMissingReservations = false)
    val router = new TestRouter(
      fixtures = new TestFixtures {
        override lazy val reservationStore: MockReservationStore = testReservationStore

        override def testNodes: Seq[Node] = Seq(localNode)
      }
    )

    testReservationStore.reservations.await.size should be(0)

    router.reserve(request = CrateStorageRequest(testManifest)).await

    testReservationStore.reservations.await.values.toList match {
      case reservation :: _ => reservation.crate should be(testManifest.crate)
      case Nil              => fail("Unexpected empty reservations list returned")
    }

    router.push(testManifest, Source.single(testContent)).await

    testReservationStore.reservations.await.size should be(0)

    eventually[Assertion] {
      router.fixtures.crateStore.statistics(MockCrateStore.Statistic.PersistCompleted) should be(1)
      router.fixtures.crateStore.statistics(MockCrateStore.Statistic.PersistFailed) should be(0)
    }
  }

  it should "fail to push crates if no reservation is available" in {
    val testReservationStore = new MockReservationStore(ignoreMissingReservations = false)
    val router = new TestRouter(
      fixtures = new TestFixtures {
        override lazy val reservationStore: MockReservationStore = testReservationStore

        override def testNodes: Seq[Node] = Seq(localNode)
      }
    )

    testReservationStore.reservations.await.size should be(0)

    router
      .push(testManifest, Source.single(testContent))
      .map { response =>
        fail(s"Received unexpected push response from router: [$response]")
      }
      .recover { case PushFailure(message) =>
        message should be(s"Push of crate [${testManifest.crate}] failed; unable to remove reservation for crate")
        router.fixtures.crateStore.statistics(MockCrateStore.Statistic.PersistCompleted) should be(0)
        router.fixtures.crateStore.statistics(MockCrateStore.Statistic.PersistFailed) should be(0)
      }
  }

  it should "fail to push crates when no nodes are available" in {
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
      .recover { case PushFailure(message) =>
        message should be(
          s"Push of crate [${testManifest.crate}] failed: [DistributionFailure - No nodes provided]"
        )
        router.fixtures.crateStore.statistics(MockCrateStore.Statistic.PersistCompleted) should be(0)
        router.fixtures.crateStore.statistics(MockCrateStore.Statistic.PersistFailed) should be(0)
        router.testClient.statistics(MockHttpEndpointClient.Statistic.PushCompleted) should be(0)
        router.testClient.statistics(MockHttpEndpointClient.Statistic.PushFailed) should be(0)
      }
  }

  it should "fail to push crates when no copies are requested" in {
    val router = new TestRouter()

    router
      .push(testManifest.copy(copies = 0), Source.single(testContent))
      .map { response =>
        fail(s"Received unexpected push response from router: [$response]")
      }
      .recover { case PushFailure(message) =>
        message should be(
          s"Push of crate [${testManifest.crate}] failed: [DistributionFailure - No copies requested]"
        )
        router.fixtures.crateStore.statistics(MockCrateStore.Statistic.PersistCompleted) should be(0)
        router.fixtures.crateStore.statistics(MockCrateStore.Statistic.PersistFailed) should be(0)
        router.testClient.statistics(MockHttpEndpointClient.Statistic.PushCompleted) should be(0)
        router.testClient.statistics(MockHttpEndpointClient.Statistic.PushFailed) should be(0)
      }
  }

  it should "recover from node failure on push" in {
    val fixtures = new TestFixtures {}

    val router = new TestRouter(
      fixtures = fixtures,
      testClient = new MockHttpEndpointClient(
        pushFailureAddresses = Map(
          fixtures.remoteNodes.last.address -> new RuntimeException("test failure")
        )
      )
    )

    router.push(testManifest, Source.single(testContent)).await

    val failedNodesCount = 1

    eventually[Assertion] {
      router.fixtures.crateStore.statistics(MockCrateStore.Statistic.PersistCompleted) should be(1)
      router.fixtures.crateStore.statistics(MockCrateStore.Statistic.PersistFailed) should be(0)
      router.testClient.crateCopies(testManifest.crate).await should be(
        router.fixtures.remoteNodes.size - failedNodesCount
      )
      router.testClient.crateNodes(testManifest.crate).await should be(
        router.fixtures.remoteNodes.size - failedNodesCount
      )
      router.testClient.statistics(MockHttpEndpointClient.Statistic.PushCompleted) should be(
        router.fixtures.remoteNodes.size - failedNodesCount
      )
      router.testClient.statistics(MockHttpEndpointClient.Statistic.PushFailed) should be(failedNodesCount)
    }
  }

  it should "fail to push crates when all nodes fail" in {
    val fixtures = new TestFixtures {
      override def testNodes: Seq[Node] = remoteNodes
    }

    val router = new TestRouter(
      fixtures = fixtures,
      testClient = new MockHttpEndpointClient(
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
      .recover { case PushFailure(message) =>
        message should be(
          s"Crate [${testManifest.crate}] was not pushed; no content sinks retrieved"
        )

        router.fixtures.crateStore.statistics(MockCrateStore.Statistic.PersistCompleted) should be(0)
        router.fixtures.crateStore.statistics(MockCrateStore.Statistic.PersistFailed) should be(0)
        router.testClient.crateCopies(testManifest.crate).await should be(0)
        router.testClient.crateNodes(testManifest.crate).await should be(0)
        router.testClient.statistics(MockHttpEndpointClient.Statistic.PushCompleted) should be(0)
        router.testClient.statistics(MockHttpEndpointClient.Statistic.PushFailed) should be(fixtures.remoteNodes.size)
      }
  }

  it should "pull crates from remote nodes" in {
    val router = new TestRouter(
      fixtures = new TestFixtures {
        override def testNodes: Seq[Node] = remoteNodes
      }
    )

    router.push(testManifest, Source.single(testContent)).await

    eventually[Assertion] {
      router.testClient.statistics(MockHttpEndpointClient.Statistic.PushCompleted) should be(
        router.fixtures.remoteNodes.size
      )
      router.testClient.statistics(MockHttpEndpointClient.Statistic.PushFailed) should be(0)
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
    router.testClient.statistics(MockHttpEndpointClient.Statistic.PullCompletedWithData) should be(1)
    router.testClient.statistics(MockHttpEndpointClient.Statistic.PullCompletedEmpty) should be(0)
    router.testClient.statistics(MockHttpEndpointClient.Statistic.PullFailed) should be(0)
  }

  it should "pull crates from local nodes" in {
    val router = new TestRouter(
      fixtures = new TestFixtures {
        override def testNodes: Seq[Node] = Seq(localNode)
      }
    )

    router.push(testManifest, Source.single(testContent)).await

    eventually[Assertion] {
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
    router.testClient.statistics(MockHttpEndpointClient.Statistic.PullCompletedWithData) should be(0)
    router.testClient.statistics(MockHttpEndpointClient.Statistic.PullCompletedEmpty) should be(0)
    router.testClient.statistics(MockHttpEndpointClient.Statistic.PullFailed) should be(0)
  }

  it should "fail to pull crates when crate manifest is missing" in {
    val router = new TestRouter()

    val missingCrateId = Crate.generateId()

    router
      .pull(missingCrateId)
      .map { response =>
        response should be(None)
        router.fixtures.crateStore.statistics(MockCrateStore.Statistic.RetrieveCompleted) should be(0)
        router.fixtures.crateStore.statistics(MockCrateStore.Statistic.RetrieveEmpty) should be(0)
        router.fixtures.crateStore.statistics(MockCrateStore.Statistic.RetrieveFailed) should be(0)
        router.testClient.statistics(MockHttpEndpointClient.Statistic.PullCompletedWithData) should be(0)
        router.testClient.statistics(MockHttpEndpointClient.Statistic.PullCompletedEmpty) should be(0)
        router.testClient.statistics(MockHttpEndpointClient.Statistic.PullFailed) should be(0)
      }
  }

  it should "fail to pull crates if crate manifest has no destinations" in {
    val router = new TestRouter()

    router.fixtures.manifestStore.put(testManifest).await

    router
      .pull(testManifest.crate)
      .map { response =>
        fail(s"Received unexpected pull response from router: [$response]")
      }
      .recover { case PullFailure(message) =>
        message should be(s"Crate [${testManifest.crate}] was not pulled; no destinations found")
        router.fixtures.crateStore.statistics(MockCrateStore.Statistic.RetrieveCompleted) should be(0)
        router.fixtures.crateStore.statistics(MockCrateStore.Statistic.RetrieveEmpty) should be(0)
        router.fixtures.crateStore.statistics(MockCrateStore.Statistic.RetrieveFailed) should be(0)
        router.testClient.statistics(MockHttpEndpointClient.Statistic.PullCompletedWithData) should be(0)
        router.testClient.statistics(MockHttpEndpointClient.Statistic.PullCompletedEmpty) should be(0)
        router.testClient.statistics(MockHttpEndpointClient.Statistic.PullFailed) should be(0)
      }
  }

  it should "recover from missing node on pull" in {
    val router = new TestRouter()

    router.push(testManifest, Source.single(testContent)).await

    eventually[Assertion] {
      router.fixtures.crateStore.statistics(MockCrateStore.Statistic.PersistCompleted) should be(1)
      router.fixtures.crateStore.statistics(MockCrateStore.Statistic.PersistFailed) should be(0)
      router.testClient.statistics(MockHttpEndpointClient.Statistic.PushCompleted) should be(
        router.fixtures.remoteNodes.size
      )
      router.testClient.statistics(MockHttpEndpointClient.Statistic.PushFailed) should be(0)
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
    router.testClient.statistics(MockHttpEndpointClient.Statistic.PullCompletedWithData) should be(1)
    router.testClient.statistics(MockHttpEndpointClient.Statistic.PullCompletedEmpty) should be(0)
    router.testClient.statistics(MockHttpEndpointClient.Statistic.PullFailed) should be(0)
  }

  it should "recover from content not returned by individual nodes" in {
    val router = new TestRouter(
      fixtures = new TestFixtures {
        override lazy val crateStore: MockCrateStore =
          new MockCrateStore(retrieveEmpty = true)
      }
    )

    router.push(testManifest, Source.single(testContent)).await

    eventually[Assertion] {
      router.fixtures.crateStore.statistics(MockCrateStore.Statistic.PersistCompleted) should be(1)
      router.fixtures.crateStore.statistics(MockCrateStore.Statistic.PersistFailed) should be(0)
      router.testClient.statistics(MockHttpEndpointClient.Statistic.PushCompleted) should be(
        router.fixtures.remoteNodes.size
      )
      router.testClient.statistics(MockHttpEndpointClient.Statistic.PushFailed) should be(0)
    }

    val result = router
      .pull(testManifest.crate)
      .flatMap(_.getOrElse(Source.empty).runFold(ByteString.empty)(_ ++ _))
      .await

    result should be(testContent)
    router.fixtures.crateStore.statistics(MockCrateStore.Statistic.RetrieveCompleted) should be(0)
    router.fixtures.crateStore.statistics(MockCrateStore.Statistic.RetrieveEmpty) should be(1)
    router.fixtures.crateStore.statistics(MockCrateStore.Statistic.RetrieveFailed) should be(0)
    router.testClient.statistics(MockHttpEndpointClient.Statistic.PullCompletedWithData) should be(1)
    router.testClient.statistics(MockHttpEndpointClient.Statistic.PullCompletedEmpty) should be(0)
    router.testClient.statistics(MockHttpEndpointClient.Statistic.PullFailed) should be(0)
  }

  it should "recover from node failure on pull" in {
    val router = new TestRouter(
      fixtures = new TestFixtures {
        override lazy val crateStore: MockCrateStore =
          new MockCrateStore(retrieveDisabled = true)
      }
    )

    router.push(testManifest, Source.single(testContent)).await

    eventually[Assertion] {
      router.fixtures.crateStore.statistics(MockCrateStore.Statistic.PersistCompleted) should be(1)
      router.fixtures.crateStore.statistics(MockCrateStore.Statistic.PersistFailed) should be(0)
      router.testClient.statistics(MockHttpEndpointClient.Statistic.PushCompleted) should be(
        router.fixtures.remoteNodes.size
      )
      router.testClient.statistics(MockHttpEndpointClient.Statistic.PushFailed) should be(0)
    }

    val result = router
      .pull(testManifest.crate)
      .flatMap(_.getOrElse(Source.empty).runFold(ByteString.empty)(_ ++ _))
      .await

    result should be(testContent)
    router.fixtures.crateStore.statistics(MockCrateStore.Statistic.RetrieveCompleted) should be(0)
    router.fixtures.crateStore.statistics(MockCrateStore.Statistic.RetrieveEmpty) should be(0)
    router.fixtures.crateStore.statistics(MockCrateStore.Statistic.RetrieveFailed) should be(1)
    router.testClient.statistics(MockHttpEndpointClient.Statistic.PullCompletedWithData) should be(1)
    router.testClient.statistics(MockHttpEndpointClient.Statistic.PullCompletedEmpty) should be(0)
    router.testClient.statistics(MockHttpEndpointClient.Statistic.PullFailed) should be(0)
  }

  it should "fail to pull missing crates" in {
    val fixtures = new TestFixtures {
      override def testNodes: Seq[Node] = remoteNodes
    }

    val router = new TestRouter(
      fixtures = fixtures,
      testClient = new MockHttpEndpointClient(
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
    router.testClient.statistics(MockHttpEndpointClient.Statistic.PullCompletedWithData) should be(0)
    router.testClient.statistics(MockHttpEndpointClient.Statistic.PullCompletedEmpty) should be(fixtures.testNodes.size)
    router.testClient.statistics(MockHttpEndpointClient.Statistic.PullFailed) should be(0)
  }

  it should "successfully discard existing crates" in {
    val router = new TestRouter()

    router.push(testManifest, Source.single(testContent)).await

    eventually[Assertion] {
      router.fixtures.crateStore.statistics(MockCrateStore.Statistic.PersistCompleted) should be(1)
      router.fixtures.crateStore.statistics(MockCrateStore.Statistic.PersistFailed) should be(0)
    }

    router.discard(testManifest.crate).await

    router.fixtures.crateStore.statistics(MockCrateStore.Statistic.DiscardCompleted) should be(1)
    router.fixtures.crateStore.statistics(MockCrateStore.Statistic.DiscardFailed) should be(0)
    router.testClient.statistics(MockHttpEndpointClient.Statistic.PushCompleted) should be(2)
    router.testClient.statistics(MockHttpEndpointClient.Statistic.PushFailed) should be(0)
    router.testClient.statistics(MockHttpEndpointClient.Statistic.DiscardCompleted) should be(2)
    router.testClient.statistics(MockHttpEndpointClient.Statistic.DiscardFailed) should be(0)
  }

  it should "successfully discard staged crates" in {
    val stagingCrateStore = new MockCrateStore()
    val testClient = new MockHttpEndpointClient()

    val fixtures = new TestFixtures {
      override lazy val stagingStore: Option[StagingStore] =
        Some(new StagingStore(crateStore = stagingCrateStore, destagingDelay = 10.seconds))
    }

    val router = new TestRouter(fixtures, testClient)

    router.push(testManifest, Source.single(testContent)).await

    eventually[Assertion] {
      stagingCrateStore.statistics(MockCrateStore.Statistic.PersistCompleted) should be(1)
      stagingCrateStore.statistics(MockCrateStore.Statistic.PersistFailed) should be(0)
    }

    router.discard(testManifest.crate).await

    router.fixtures.crateStore.statistics(MockCrateStore.Statistic.PersistCompleted) should be(0)
    router.fixtures.crateStore.statistics(MockCrateStore.Statistic.PersistFailed) should be(0)
    router.fixtures.crateStore.statistics(MockCrateStore.Statistic.DiscardCompleted) should be(0)
    router.fixtures.crateStore.statistics(MockCrateStore.Statistic.DiscardFailed) should be(0)
    router.testClient.statistics(MockHttpEndpointClient.Statistic.PushCompleted) should be(0)
    router.testClient.statistics(MockHttpEndpointClient.Statistic.PushFailed) should be(0)
    router.testClient.statistics(MockHttpEndpointClient.Statistic.DiscardCompleted) should be(0)
    router.testClient.statistics(MockHttpEndpointClient.Statistic.DiscardFailed) should be(0)
  }

  it should "fail to discard crates with no manifest" in {
    val router = new TestRouter()

    router
      .discard(testManifest.crate)
      .map { response =>
        fail(s"Received unexpected response from router: [$response]")
      }
      .recover { case DiscardFailure(message) =>
        message should be(s"Crate [${testManifest.crate}] was not discarded; failed to retrieve manifest")

        router.fixtures.crateStore.statistics(MockCrateStore.Statistic.PersistCompleted) should be(0)
        router.fixtures.crateStore.statistics(MockCrateStore.Statistic.PersistFailed) should be(0)
        router.fixtures.crateStore.statistics(MockCrateStore.Statistic.DiscardCompleted) should be(0)
        router.fixtures.crateStore.statistics(MockCrateStore.Statistic.DiscardFailed) should be(0)
        router.testClient.statistics(MockHttpEndpointClient.Statistic.PushCompleted) should be(0)
        router.testClient.statistics(MockHttpEndpointClient.Statistic.PushFailed) should be(0)
        router.testClient.statistics(MockHttpEndpointClient.Statistic.DiscardCompleted) should be(0)
        router.testClient.statistics(MockHttpEndpointClient.Statistic.DiscardFailed) should be(0)
      }
  }

  it should "fail to discard crates with no destinations" in {
    val router = new TestRouter()

    router.fixtures.manifestStore.put(testManifest).await

    router
      .discard(testManifest.crate)
      .map { response =>
        fail(s"Received unexpected pull response from router: [$response]")
      }
      .recover { case DiscardFailure(message) =>
        message should be(s"Crate [${testManifest.crate}] was not discarded; no destinations found")

        router.fixtures.crateStore.statistics(MockCrateStore.Statistic.PersistCompleted) should be(0)
        router.fixtures.crateStore.statistics(MockCrateStore.Statistic.PersistFailed) should be(0)
        router.fixtures.crateStore.statistics(MockCrateStore.Statistic.DiscardCompleted) should be(0)
        router.fixtures.crateStore.statistics(MockCrateStore.Statistic.DiscardFailed) should be(0)
        router.testClient.statistics(MockHttpEndpointClient.Statistic.PushCompleted) should be(0)
        router.testClient.statistics(MockHttpEndpointClient.Statistic.PushFailed) should be(0)
        router.testClient.statistics(MockHttpEndpointClient.Statistic.DiscardCompleted) should be(0)
        router.testClient.statistics(MockHttpEndpointClient.Statistic.DiscardFailed) should be(0)
      }
  }

  it should "fail to discard crates with some missing nodes" in {
    val router = new TestRouter()

    router.push(testManifest, Source.single(testContent)).await

    eventually[Assertion] {
      router.fixtures.crateStore.statistics(MockCrateStore.Statistic.PersistCompleted) should be(1)
      router.fixtures.crateStore.statistics(MockCrateStore.Statistic.PersistFailed) should be(0)
      router.testClient.statistics(MockHttpEndpointClient.Statistic.PushCompleted) should be(
        router.fixtures.remoteNodes.size
      )
      router.testClient.statistics(MockHttpEndpointClient.Statistic.PushFailed) should be(0)
    }

    val missingNodeId = Node.generateId()

    router.fixtures.manifestStore
      .put(testManifest.copy(destinations = router.fixtures.testNodes.map(_.id) :+ missingNodeId))
      .await

    router
      .discard(testManifest.crate)
      .map { response =>
        fail(s"Received unexpected pull response from router: [$response]")
      }
      .recover { case DiscardFailure(message) =>
        message should be(
          s"Crate [${testManifest.crate}] was not discarded; crate or nodes missing"
        )

        router.fixtures.crateStore.statistics(MockCrateStore.Statistic.PersistCompleted) should be(1)
        router.fixtures.crateStore.statistics(MockCrateStore.Statistic.PersistFailed) should be(0)
        router.fixtures.crateStore.statistics(MockCrateStore.Statistic.DiscardCompleted) should be(1)
        router.fixtures.crateStore.statistics(MockCrateStore.Statistic.DiscardFailed) should be(0)
        router.testClient.statistics(MockHttpEndpointClient.Statistic.PushCompleted) should be(2)
        router.testClient.statistics(MockHttpEndpointClient.Statistic.PushFailed) should be(0)
        router.testClient.statistics(MockHttpEndpointClient.Statistic.DiscardCompleted) should be(2)
        router.testClient.statistics(MockHttpEndpointClient.Statistic.DiscardFailed) should be(0)
      }
  }

  it should "fail to discard crates with all nodes missing" in {
    val router = new TestRouter()

    router.push(testManifest, Source.single(testContent)).await

    eventually[Assertion] {
      router.fixtures.crateStore.statistics(MockCrateStore.Statistic.PersistCompleted) should be(1)
      router.fixtures.crateStore.statistics(MockCrateStore.Statistic.PersistFailed) should be(0)
      router.testClient.statistics(MockHttpEndpointClient.Statistic.PushCompleted) should be(2)
      router.testClient.statistics(MockHttpEndpointClient.Statistic.PushFailed) should be(0)
    }

    val missingNodeId = Node.generateId()

    router.fixtures.manifestStore
      .put(testManifest.copy(destinations = Seq(missingNodeId)))
      .await

    router
      .discard(testManifest.crate)
      .map { response =>
        fail(s"Received unexpected pull response from router: [$response]")
      }
      .recover { case DiscardFailure(message) =>
        message should be(
          s"Crate [${testManifest.crate}] was not discarded; crate or nodes missing"
        )

        router.fixtures.crateStore.statistics(MockCrateStore.Statistic.PersistCompleted) should be(1)
        router.fixtures.crateStore.statistics(MockCrateStore.Statistic.PersistFailed) should be(0)
        router.fixtures.crateStore.statistics(MockCrateStore.Statistic.DiscardCompleted) should be(0)
        router.fixtures.crateStore.statistics(MockCrateStore.Statistic.DiscardFailed) should be(0)
        router.testClient.statistics(MockHttpEndpointClient.Statistic.PushCompleted) should be(2)
        router.testClient.statistics(MockHttpEndpointClient.Statistic.PushFailed) should be(0)
        router.testClient.statistics(MockHttpEndpointClient.Statistic.DiscardCompleted) should be(0)
        router.testClient.statistics(MockHttpEndpointClient.Statistic.DiscardFailed) should be(0)
      }
  }

  it should "handle partial discards" in {
    val router = new TestRouter(
      fixtures = new TestFixtures {
        override lazy val crateStore: MockCrateStore =
          new MockCrateStore(discardDisabled = true)
      }
    )

    router.push(testManifest, Source.single(testContent)).await

    eventually[Assertion] {
      router.fixtures.crateStore.statistics(MockCrateStore.Statistic.PersistCompleted) should be(1)
      router.fixtures.crateStore.statistics(MockCrateStore.Statistic.PersistFailed) should be(0)
    }

    router
      .discard(testManifest.crate)
      .map { response =>
        fail(s"Received unexpected pull response from router: [$response]")
      }
      .recover { case DiscardFailure(message) =>
        message should be(
          s"Crate [${testManifest.crate}] was not discarded; crate or nodes missing"
        )

        router.fixtures.manifestStore.get(testManifest.crate).await match {
          case Some(manifest) =>
            manifest.destinations should be(Seq(router.fixtures.localNode.id))

          case None =>
            fail(s"Expected manifest for crate [${testManifest.crate}] from store but none was found")
        }

        router.fixtures.crateStore.statistics(MockCrateStore.Statistic.DiscardCompleted) should be(0)
        router.fixtures.crateStore.statistics(MockCrateStore.Statistic.DiscardFailed) should be(1)
        router.testClient.statistics(MockHttpEndpointClient.Statistic.PushCompleted) should be(2)
        router.testClient.statistics(MockHttpEndpointClient.Statistic.PushFailed) should be(0)
        router.testClient.statistics(MockHttpEndpointClient.Statistic.DiscardCompleted) should be(2)
        router.testClient.statistics(MockHttpEndpointClient.Statistic.DiscardFailed) should be(0)
      }
  }

  it should "process reservation requests for local nodes" in {
    val router = new TestRouter(
      fixtures = new TestFixtures {
        override def testNodes: Seq[Node] = Seq(localNode)
      }
    )

    val request = CrateStorageRequest(
      crate = Crate.generateId(),
      size = 1,
      copies = 1,
      origin = Node.generateId(),
      source = Node.generateId()
    )

    val expectedReservation = CrateStorageReservation(
      request = request,
      target = Node.generateId()
    )

    for {
      response <- router.reserve(request)
    } yield {
      response should be(defined)

      val actualReservation = response.get
      actualReservation.size should be(expectedReservation.size)
      actualReservation.copies should be(expectedReservation.copies)
    }
  }

  it should "not process reservation requests for remote nodes" in {
    val router = new TestRouter(
      fixtures = new TestFixtures {
        override def testNodes: Seq[Node] = remoteNodes
      }
    )

    val request = CrateStorageRequest(
      crate = Crate.generateId(),
      size = 1,
      copies = 1,
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
      crate = Crate.generateId(),
      size = 1,
      copies = 1,
      origin = Node.generateId(),
      source = Node.generateId()
    )

    router.reserve(request).map(_ should be(None))
  }

  it should "recover from nodes failing to process reservation requests" in {
    val router = new TestRouter(
      fixtures = new TestFixtures {
        override lazy val crateStore: MockCrateStore =
          new MockCrateStore(maxStorageSize = Some(0))
      }
    )

    val request = CrateStorageRequest(
      crate = Crate.generateId(),
      size = 1,
      copies = 1,
      origin = Node.generateId(),
      source = Node.generateId()
    )

    router.reserve(request).map(_ should be(None))
  }

  it should "fail to process reservation requests if reservation already exists" in {
    val router = new TestRouter(
      fixtures = new TestFixtures {
        override def testNodes: Seq[Node] = Seq(localNode)
      }
    )

    val request = CrateStorageRequest(
      crate = Crate.generateId(),
      size = 1,
      copies = 1,
      origin = Node.generateId(),
      source = Node.generateId()
    )

    val expectedReservation = CrateStorageReservation(
      request = request,
      target = Node.generateId()
    )

    router.reserve(request).await match {
      case Some(actualReservation) =>
        actualReservation.size should be(expectedReservation.size)
        actualReservation.copies should be(expectedReservation.copies)

      case None => fail("Expected reservation response but none was received")
    }

    router.reserve(request).map(_ should be(None))
  }

  private implicit val system: ActorSystem[SpawnProtocol.Command] = ActorSystem(
    Behaviors.setup(_ => SpawnProtocol()): Behavior[SpawnProtocol.Command],
    "DefaultRouterSpec"
  )

  private implicit val ec: ExecutionContext = system.executionContext

  override implicit val patienceConfig: PatienceConfig = PatienceConfig(5.seconds, 250.milliseconds)

  private trait TestFixtures {
    lazy val reservationStore: MockReservationStore = new MockReservationStore()
    lazy val crateStore: MockCrateStore = new MockCrateStore()
    lazy val manifestStore: MockManifestStore = new MockManifestStore
    lazy val nodeStore: MockNodeStore = new MockNodeStore
    lazy val localNode: Node.Local = Node.Local(
      id = Node.generateId(),
      storeDescriptor = null /* mock crate store is always provided in this test */
    )
    lazy val stagingStore: Option[StagingStore] = None
    lazy val remoteNodes: Seq[Node.Remote.Http] = Seq(
      Node.Remote.Http(Node.generateId(), address = HttpEndpointAddress("localhost:8000"), storageAllowed = true),
      Node.Remote.Http(Node.generateId(), address = HttpEndpointAddress("localhost:9000"), storageAllowed = true)
    )

    def testNodes: Seq[Node] =
      Seq(
        remoteNodes.head,
        localNode,
        remoteNodes.last
      )

    Future.sequence(testNodes.map(node => nodeStore.put(node))).await
  }

  private class TestRouter(
    val fixtures: TestFixtures = new TestFixtures {},
    val testClient: MockHttpEndpointClient = new MockHttpEndpointClient()
  ) extends DefaultRouter(
        routerId = Node.generateId(),
        persistence = DefaultRouter.Persistence(
          manifests = fixtures.manifestStore,
          nodes = fixtures.nodeStore.view,
          reservations = fixtures.reservationStore,
          staging = fixtures.stagingStore
        ),
        nodeProxy = new NodeProxy(
          httpClient = testClient,
          grpcClient = new MockGrpcEndpointClient()
        ) {
          override protected def crateStore(id: Node.Id, storeDescriptor: CrateStore.Descriptor): Future[CrateStore] =
            Future.successful(fixtures.crateStore)
        }
      )

  private val testContent = ByteString("some value")

  private val testManifest = Manifest(
    crate = Crate.generateId(),
    size = testContent.size.toLong,
    copies = 4,
    source = Node.generateId(),
    origin = Node.generateId()
  )
}
