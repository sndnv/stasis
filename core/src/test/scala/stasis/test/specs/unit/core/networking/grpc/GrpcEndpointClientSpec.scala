package stasis.test.specs.unit.core.networking.grpc

import scala.collection.mutable
import scala.concurrent.duration._
import scala.util.control.NonFatal

import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import org.apache.pekko.actor.typed.ActorSystem
import org.apache.pekko.actor.typed.scaladsl.Behaviors
import org.apache.pekko.stream.scaladsl.Source
import org.apache.pekko.util.ByteString
import org.scalatest.Assertion
import org.scalatest.concurrent.Eventually

import stasis.core.networking.grpc.GrpcEndpoint
import stasis.core.networking.grpc.GrpcEndpointAddress
import stasis.core.networking.grpc.GrpcEndpointClient
import stasis.core.packaging.Crate
import stasis.core.packaging.Manifest
import stasis.core.routing.Node
import stasis.layers.security.tls.EndpointContext
import stasis.layers.telemetry.TelemetryContext
import stasis.test.specs.unit.AsyncUnitSpec
import stasis.test.specs.unit.core.networking.mocks.MockGrpcNodeCredentialsProvider
import stasis.test.specs.unit.core.persistence.mocks.MockCrateStore
import stasis.test.specs.unit.core.persistence.mocks.MockReservationStore
import stasis.test.specs.unit.core.routing.mocks.MockRouter
import stasis.test.specs.unit.core.security.mocks.MockGrpcAuthenticator
import stasis.test.specs.unit.core.telemetry.MockTelemetryContext

class GrpcEndpointClientSpec extends AsyncUnitSpec with Eventually {
  "An GRPC Endpoint Client" should "successfully push crates" in withRetry {
    val endpointPort = ports.dequeue()
    val endpointAddress = GrpcEndpointAddress("localhost", endpointPort, tlsEnabled = false)

    val endpoint = createTestGrpcEndpoint(port = endpointPort)

    val client = GrpcEndpointClient(
      credentials = new MockGrpcNodeCredentialsProvider(endpointAddress, testNode, testSecret),
      maxChunkSize = maxChunkSize
    )

    client.push(endpointAddress, testManifest, Source.single(ByteString(crateContent))).flatMap { _ =>
      eventually[Assertion] {
        endpoint.fixtures.crateStore.statistics(MockCrateStore.Statistic.PersistCompleted) should be(1)
        endpoint.fixtures.crateStore.statistics(MockCrateStore.Statistic.PersistFailed) should be(0)
      }
    }
  }

  it should "fail to push crates if no credentials are available" in withRetry {
    val endpointPort = ports.dequeue()
    val endpointAddress = GrpcEndpointAddress("localhost", endpointPort, tlsEnabled = false)

    val _ = createTestGrpcEndpoint(port = endpointPort)

    val client = GrpcEndpointClient(
      credentials = new MockGrpcNodeCredentialsProvider(Map.empty),
      maxChunkSize = maxChunkSize
    )

    client
      .push(endpointAddress, testManifest, Source.single(ByteString(crateContent)))
      .map { response =>
        fail(s"Received unexpected response from endpoint: [$response]")
      }
      .recover { case NonFatal(e) =>
        e.getMessage should be(
          s"Push to endpoint [${endpointAddress.host}] failed for crate [${testManifest.crate}]; " +
            s"unable to retrieve credentials: [No credentials found for [GrpcEndpointAddress(localhost,$endpointPort,false)]]"
        )
      }
  }

  it should "successfully push crates via a stream sink" in withRetry {
    val endpointPort = ports.dequeue()
    val endpointAddress = GrpcEndpointAddress("localhost", endpointPort, tlsEnabled = false)

    val endpoint = createTestGrpcEndpoint(port = endpointPort)

    val client = GrpcEndpointClient(
      credentials = new MockGrpcNodeCredentialsProvider(endpointAddress, testNode, testSecret),
      maxChunkSize = maxChunkSize
    )

    client.push(endpointAddress, testManifest).flatMap { sink =>
      Source
        .single(ByteString(crateContent))
        .runWith(sink)
        .map { _ =>
          eventually[Assertion] {
            endpoint.fixtures.crateStore.statistics(MockCrateStore.Statistic.PersistCompleted) should be(1)
            endpoint.fixtures.crateStore.statistics(MockCrateStore.Statistic.PersistFailed) should be(0)
          }
        }
    }
  }

  it should "handle reservation rejections when pushing via a stream sink" in withRetry {
    val endpointPort = ports.dequeue()
    val endpointAddress = GrpcEndpointAddress("localhost", endpointPort, tlsEnabled = false)

    val endpoint = createTestGrpcEndpoint(port = endpointPort)

    val client = GrpcEndpointClient(
      credentials = new MockGrpcNodeCredentialsProvider(endpointAddress, testNode, testSecret),
      maxChunkSize = maxChunkSize
    )

    client
      .push(endpointAddress, testManifest.copy(size = 100))
      .flatMap(sink => Source.single(ByteString(crateContent)).runWith(sink))
      .map { response =>
        fail(s"Received unexpected response from endpoint: [$response]")
      }
      .recover { case NonFatal(e) =>
        e.getMessage should be(
          s"Reservation on endpoint [${endpointAddress.host}] failed for crate [${testManifest.crate}]: " +
            s"[Reservation rejected for node [$testNode]]"
        )
        endpoint.fixtures.crateStore.statistics(MockCrateStore.Statistic.PersistCompleted) should be(0)
        endpoint.fixtures.crateStore.statistics(MockCrateStore.Statistic.PersistFailed) should be(0)
      }
  }

  it should "handle push failures via a stream sink" in withRetry {
    val endpointPort = ports.dequeue()
    val endpointAddress = GrpcEndpointAddress("localhost", endpointPort, tlsEnabled = false)

    implicit val telemetry: MockTelemetryContext = MockTelemetryContext()

    val endpoint = createTestGrpcEndpoint(
      port = endpointPort,
      fixtures = Some(
        new TestFixtures {
          override lazy val crateStore: MockCrateStore = new MockCrateStore(persistDisabled = true)
        }
      )
    )

    val client = GrpcEndpointClient(
      credentials = new MockGrpcNodeCredentialsProvider(endpointAddress, testNode, testSecret),
      maxChunkSize = maxChunkSize
    )

    client
      .push(endpointAddress, testManifest)
      .flatMap(sink => Source.single(ByteString(crateContent)).runWith(sink))
      .map { response =>
        fail(s"Received unexpected response from endpoint: [$response]")
      }
      .recover { case NonFatal(e) =>
        e.getMessage should be(
          s"Push to endpoint [${endpointAddress.host}] failed for crate [${testManifest.crate}]: " +
            s"[Push failed for node [$testNode]: [PersistenceFailure - [persistDisabled] is set to [true]]]"
        )
        endpoint.fixtures.crateStore.statistics(MockCrateStore.Statistic.PersistCompleted) should be(0)
        endpoint.fixtures.crateStore.statistics(MockCrateStore.Statistic.PersistFailed) should be(1)
      }
  }

  it should "successfully pull crates" in withRetry {
    val endpointPort = ports.dequeue()
    val endpointAddress = GrpcEndpointAddress("localhost", endpointPort, tlsEnabled = false)

    val endpoint = createTestGrpcEndpoint(port = endpointPort)

    val client = GrpcEndpointClient(
      credentials = new MockGrpcNodeCredentialsProvider(endpointAddress, testNode, testSecret),
      maxChunkSize = maxChunkSize
    )

    client
      .push(endpointAddress, testManifest)
      .flatMap(sink => Source.single(ByteString(crateContent)).runWith(sink))
      .flatMap { _ =>
        client.pull(endpointAddress, testManifest.crate).flatMap {
          case Some(source) =>
            source
              .runFold(ByteString.empty) { case (folded, chunk) =>
                folded.concat(chunk)
              }
              .map { result =>
                result.utf8String should be(crateContent)
                endpoint.fixtures.crateStore.statistics(MockCrateStore.Statistic.PersistCompleted) should be(1)
                endpoint.fixtures.crateStore.statistics(MockCrateStore.Statistic.PersistFailed) should be(0)
                endpoint.fixtures.crateStore.statistics(MockCrateStore.Statistic.RetrieveCompleted) should be(1)
                endpoint.fixtures.crateStore.statistics(MockCrateStore.Statistic.RetrieveEmpty) should be(0)
                endpoint.fixtures.crateStore.statistics(MockCrateStore.Statistic.RetrieveFailed) should be(0)
              }

          case None =>
            fail("Received unexpected empty response")
        }
      }
  }

  it should "handle trying to pull missing crates" in withRetry {
    val endpointPort = ports.dequeue()
    val endpointAddress = GrpcEndpointAddress("localhost", endpointPort, tlsEnabled = false)

    val _ = createTestGrpcEndpoint(port = endpointPort)

    val client = GrpcEndpointClient(
      credentials = new MockGrpcNodeCredentialsProvider(endpointAddress, testNode, testSecret),
      maxChunkSize = maxChunkSize
    )

    client.pull(endpointAddress, Crate.generateId()).map { response =>
      response should be(None)
    }
  }

  it should "handle pull failures" in withRetry {
    val endpointPort = ports.dequeue()
    val endpointAddress = GrpcEndpointAddress("localhost", endpointPort, tlsEnabled = false)

    val _ = createTestGrpcEndpoint(port = endpointPort)

    val client = GrpcEndpointClient(
      credentials = new MockGrpcNodeCredentialsProvider(endpointAddress, testNode, "invalid-secret"),
      maxChunkSize = maxChunkSize
    )

    val crateId = Crate.generateId()

    client
      .pull(endpointAddress, crateId)
      .map { response =>
        fail(s"Received unexpected response from endpoint: [$response]")
      }
      .recover { case NonFatal(e) =>
        e.getMessage should startWith(s"Pull from endpoint [${endpointAddress.host}] failed for crate [$crateId]")
      }
  }

  it should "be able to authenticate against multiple endpoints" in withRetry {
    val primaryEndpointPort = ports.dequeue()
    val primaryEndpointAddress = GrpcEndpointAddress("localhost", primaryEndpointPort, tlsEnabled = false)
    val secondaryEndpointPort = ports.dequeue()
    val secondaryEndpointAddress = GrpcEndpointAddress("localhost", secondaryEndpointPort, tlsEnabled = false)

    val primaryEndpointNode = Node.generateId()
    val primaryEndpointSecret = "primary-endpoint-secret"
    val secondaryEndpointNode = Node.generateId()
    val secondaryEndpointSecret = "secondary-endpoint-secret"

    createTestGrpcEndpoint(
      testAuthenticator = new MockGrpcAuthenticator(primaryEndpointNode, primaryEndpointSecret),
      port = primaryEndpointPort
    )

    createTestGrpcEndpoint(
      testAuthenticator = new MockGrpcAuthenticator(secondaryEndpointNode, secondaryEndpointSecret),
      port = secondaryEndpointPort
    )

    val client = GrpcEndpointClient(
      credentials = new MockGrpcNodeCredentialsProvider(
        Map(
          primaryEndpointAddress -> (primaryEndpointNode, primaryEndpointSecret),
          secondaryEndpointAddress -> (secondaryEndpointNode, secondaryEndpointSecret)
        )
      ),
      maxChunkSize = maxChunkSize
    )

    for {
      primarySink <- client.push(primaryEndpointAddress, testManifest)
      secondarySink <- client.push(secondaryEndpointAddress, testManifest)
      _ <- Source.single(ByteString(crateContent)).runWith(primarySink)
      _ <- Source.single(ByteString(crateContent)).runWith(secondarySink)
    } yield {
      succeed
    }
  }

  it should "fail to push crates via sink if no credentials are available" in withRetry {
    val endpointPort = ports.dequeue()
    val endpointAddress = GrpcEndpointAddress("localhost", endpointPort, tlsEnabled = false)

    val _ = createTestGrpcEndpoint(port = endpointPort)

    val client = GrpcEndpointClient(
      credentials = new MockGrpcNodeCredentialsProvider(Map.empty),
      maxChunkSize = maxChunkSize
    )

    client
      .push(endpointAddress, testManifest)
      .map { response =>
        fail(s"Received unexpected response from endpoint: [$response]")
      }
      .recover { case NonFatal(e) =>
        e.getMessage should be(
          s"Push to endpoint [${endpointAddress.host}] via sink failed for crate [${testManifest.crate}]; " +
            s"unable to retrieve credentials: [No credentials found for [GrpcEndpointAddress(localhost,$endpointPort,false)]]"
        )
      }
  }

  it should "fail to pull crates if no credentials are available" in withRetry {
    val endpointPort = ports.dequeue()
    val endpointAddress = GrpcEndpointAddress("localhost", endpointPort, tlsEnabled = false)

    val _ = createTestGrpcEndpoint(port = endpointPort)

    val client = GrpcEndpointClient(
      credentials = new MockGrpcNodeCredentialsProvider(Map.empty),
      maxChunkSize = maxChunkSize
    )

    val crateId = Crate.generateId()

    client
      .pull(endpointAddress, crateId)
      .map { response =>
        fail(s"Received unexpected response from endpoint: [$response]")
      }
      .recover { case NonFatal(e) =>
        e.getMessage should be(
          s"Pull from endpoint [${endpointAddress.host}] failed for crate [$crateId]; " +
            s"unable to retrieve credentials: [No credentials found for [GrpcEndpointAddress(localhost,$endpointPort,false)]]"
        )
      }
  }

  it should "successfully discard existing crates" in withRetry {
    val endpointPort = ports.dequeue()
    val endpointAddress = GrpcEndpointAddress("localhost", endpointPort, tlsEnabled = false)

    val endpoint = createTestGrpcEndpoint(port = endpointPort)

    val client = GrpcEndpointClient(
      credentials = new MockGrpcNodeCredentialsProvider(endpointAddress, testNode, testSecret),
      maxChunkSize = maxChunkSize
    )

    client
      .push(endpointAddress, testManifest)
      .flatMap(sink => Source.single(ByteString(crateContent)).runWith(sink))
      .flatMap { _ =>
        client.discard(endpointAddress, testManifest.crate).flatMap { result =>
          result should be(true)
          endpoint.fixtures.crateStore.statistics(MockCrateStore.Statistic.PersistCompleted) should be(1)
          endpoint.fixtures.crateStore.statistics(MockCrateStore.Statistic.PersistFailed) should be(0)
          endpoint.fixtures.crateStore.statistics(MockCrateStore.Statistic.DiscardCompleted) should be(1)
          endpoint.fixtures.crateStore.statistics(MockCrateStore.Statistic.DiscardFailed) should be(0)
        }
      }
  }

  it should "fail to discard crates if no credentials are available" in withRetry {
    val endpointPort = ports.dequeue()
    val endpointAddress = GrpcEndpointAddress("localhost", endpointPort, tlsEnabled = false)

    val _ = createTestGrpcEndpoint(port = endpointPort)

    val client = GrpcEndpointClient(
      credentials = new MockGrpcNodeCredentialsProvider(Map.empty),
      maxChunkSize = maxChunkSize
    )

    val crateId = Crate.generateId()

    client
      .discard(endpointAddress, crateId)
      .map { response =>
        fail(s"Received unexpected response from endpoint: [$response]")
      }
      .recover { case NonFatal(e) =>
        e.getMessage should be(
          s"Discard from endpoint [${endpointAddress.host}] failed for crate [$crateId]; " +
            s"unable to retrieve credentials: [No credentials found for [GrpcEndpointAddress(localhost,$endpointPort,false)]]"
        )
      }
  }

  it should "fail to discard missing crates" in withRetry {
    val endpointPort = ports.dequeue()
    val endpointAddress = GrpcEndpointAddress("localhost", endpointPort, tlsEnabled = false)

    val _ = createTestGrpcEndpoint(port = endpointPort)

    val client = GrpcEndpointClient(
      credentials = new MockGrpcNodeCredentialsProvider(endpointAddress, testNode, testSecret),
      maxChunkSize = maxChunkSize
    )

    client.discard(endpointAddress, Crate.generateId()).map { result =>
      result should be(false)
    }
  }

  it should "handle discard failures" in withRetry {
    val endpointPort = ports.dequeue()
    val endpointAddress = GrpcEndpointAddress("localhost", endpointPort, tlsEnabled = false)

    val _ = createTestGrpcEndpoint(port = endpointPort)

    val client = GrpcEndpointClient(
      credentials = new MockGrpcNodeCredentialsProvider(endpointAddress, testNode, "invalid-secret"),
      maxChunkSize = maxChunkSize
    )

    client.discard(endpointAddress, Crate.generateId()).map { result =>
      result should be(false)
    }
  }

  it should "support custom connection contexts" in withRetry {
    val endpointPort = ports.dequeue()
    val endpointAddress = GrpcEndpointAddress("localhost", endpointPort, tlsEnabled = true)

    val config: Config = ConfigFactory.load().getConfig("stasis.test.core.security.tls")

    val endpointContext = EndpointContext(
      config = EndpointContext.Config(config.getConfig("context-server"))
    )

    val clientContext = EndpointContext(
      config = EndpointContext.Config(config.getConfig("context-client"))
    )

    val endpoint = createTestGrpcEndpoint(port = endpointPort, context = Some(endpointContext))

    val client = GrpcEndpointClient(
      credentials = new MockGrpcNodeCredentialsProvider(endpointAddress, testNode, testSecret),
      context = clientContext,
      maxChunkSize = maxChunkSize
    )

    client
      .push(endpointAddress, testManifest)
      .flatMap(sink => Source.single(ByteString(crateContent)).runWith(sink))
      .map { _ =>
        eventually[Assertion] {
          endpoint.fixtures.crateStore.statistics(MockCrateStore.Statistic.PersistCompleted) should be(1)
          endpoint.fixtures.crateStore.statistics(MockCrateStore.Statistic.PersistFailed) should be(0)
        }
      }
  }

  override implicit val patienceConfig: PatienceConfig = PatienceConfig(5.seconds, 250.milliseconds)

  private implicit val typedSystem: ActorSystem[Nothing] = ActorSystem(
    Behaviors.ignore,
    "GrpcEndpointClientSpec_Typed"
  )

  private val crateContent = "some value"

  private val testManifest = Manifest(
    crate = Crate.generateId(),
    size = 1,
    copies = 7,
    source = Node.generateId(),
    origin = Node.generateId()
  )

  private val testNode = Node.generateId()
  private val testSecret = "test-secret"

  private def createTestGrpcEndpoint(
    testAuthenticator: MockGrpcAuthenticator = new MockGrpcAuthenticator(testNode, testSecret),
    fixtures: Option[TestFixtures] = None,
    context: Option[EndpointContext] = None,
    port: Int
  ): TestGrpcEndpoint = {
    implicit val telemetry: MockTelemetryContext = MockTelemetryContext()

    new TestGrpcEndpoint(
      testAuthenticator = testAuthenticator,
      fixtures = fixtures.getOrElse(new TestFixtures()),
      context = context,
      port = port
    )
  }

  private class TestFixtures(implicit telemetry: TelemetryContext) {
    lazy val reservationStore: MockReservationStore = new MockReservationStore()
    lazy val crateStore: MockCrateStore = new MockCrateStore(maxStorageSize = Some(99))
    lazy val router: MockRouter = new MockRouter(crateStore, testNode, reservationStore)
  }

  private class TestGrpcEndpoint(
    val testAuthenticator: MockGrpcAuthenticator,
    val fixtures: TestFixtures,
    context: Option[EndpointContext] = None,
    port: Int
  )(implicit telemetry: TelemetryContext)
      extends GrpcEndpoint(fixtures.router, fixtures.reservationStore.view, testAuthenticator) {
    locally {
      val _ = start(
        interface = "localhost",
        port = port,
        context = context
      )
    }
  }

  private val ports: mutable.Queue[Int] = (20000 to 20100).to(mutable.Queue)

  private val maxChunkSize = 100
}
