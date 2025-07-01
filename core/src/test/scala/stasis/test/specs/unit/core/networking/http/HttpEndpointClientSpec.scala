package stasis.test.specs.unit.core.networking.http

import scala.collection.mutable
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.control.NonFatal

import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import org.apache.pekko.Done
import org.apache.pekko.actor.typed.ActorSystem
import org.apache.pekko.actor.typed.scaladsl.Behaviors
import org.apache.pekko.http.scaladsl.model.headers.HttpCredentials
import org.apache.pekko.stream.scaladsl.Source
import org.apache.pekko.util.ByteString
import org.scalatest.Assertion
import org.scalatest.concurrent.Eventually

import stasis.core.api.PoolClient
import stasis.core.networking.http.HttpEndpoint
import stasis.core.networking.http.HttpEndpointAddress
import stasis.core.networking.http.HttpEndpointClient
import stasis.core.packaging.Crate
import stasis.core.packaging.Manifest
import stasis.core.routing.Node
import stasis.core.security.NodeAuthenticator
import io.github.sndnv.layers.security.tls.EndpointContext
import io.github.sndnv.layers.telemetry.TelemetryContext
import stasis.test.specs.unit.AsyncUnitSpec
import stasis.test.specs.unit.core.networking.mocks.MockHttpNodeCredentialsProvider
import stasis.test.specs.unit.core.persistence.crates.MockCrateStore
import stasis.test.specs.unit.core.persistence.reservations.MockReservationStore
import stasis.test.specs.unit.core.routing.mocks.MockRouter
import stasis.test.specs.unit.core.security.mocks.MockHttpAuthenticator
import stasis.test.specs.unit.core.telemetry.MockTelemetryContext

class HttpEndpointClientSpec extends AsyncUnitSpec with Eventually {
  "An HTTP Endpoint Client" should "successfully push crates" in {
    val endpointPort = ports.dequeue()
    val endpointAddress = HttpEndpointAddress(s"http://localhost:$endpointPort")

    val endpoint = createTestHttpEndpoint(port = endpointPort)

    val client = HttpEndpointClient(
      credentials = new MockHttpNodeCredentialsProvider(endpointAddress, testUser, testPassword),
      maxChunkSize = maxChunkSize,
      config = PoolClient.Config.Default
    )

    client.push(endpointAddress, testManifest, Source.single(ByteString(crateContent))).flatMap { _ =>
      eventually[Assertion] {
        endpoint.fixtures.crateStore.statistics(MockCrateStore.Statistic.PersistCompleted) should be(1)
        endpoint.fixtures.crateStore.statistics(MockCrateStore.Statistic.PersistFailed) should be(0)
      }
    }
  }

  it should "fail to push crates if no credentials are available" in {
    val endpointPort = ports.dequeue()
    val endpointAddress = HttpEndpointAddress(s"http://localhost:$endpointPort")

    val _ = createTestHttpEndpoint(port = endpointPort)

    val client = HttpEndpointClient(
      credentials = new MockHttpNodeCredentialsProvider(Map.empty),
      maxChunkSize = maxChunkSize,
      config = PoolClient.Config.Default
    )

    client
      .push(endpointAddress, testManifest, Source.single(ByteString(crateContent)))
      .map { response =>
        fail(s"Received unexpected response from endpoint: [$response]")
      }
      .recover { case NonFatal(e) =>
        e.getMessage should be(
          s"Push to endpoint [${endpointAddress.uri}] failed for crate [${testManifest.crate}]; " +
            s"unable to retrieve credentials: [No credentials found for [HttpEndpointAddress(http://localhost:$endpointPort)]]"
        )
      }
  }

  it should "successfully push crates via a stream sink" in {
    val endpointPort = ports.dequeue()
    val endpointAddress = HttpEndpointAddress(s"http://localhost:$endpointPort")

    val endpoint = createTestHttpEndpoint(port = endpointPort)

    val client = HttpEndpointClient(
      credentials = new MockHttpNodeCredentialsProvider(endpointAddress, testUser, testPassword),
      maxChunkSize = maxChunkSize,
      config = PoolClient.Config.Default
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

  it should "handle reservation rejections when pushing via a stream sink" in {
    val endpointPort = ports.dequeue()
    val endpointAddress = HttpEndpointAddress(s"http://localhost:$endpointPort")

    val endpoint = createTestHttpEndpoint(port = endpointPort)

    val client = HttpEndpointClient(
      credentials = new MockHttpNodeCredentialsProvider(endpointAddress, testUser, testPassword),
      maxChunkSize = maxChunkSize,
      config = PoolClient.Config.Default
    )

    client
      .push(endpointAddress, testManifest.copy(size = 100))
      .flatMap(sink => Source.single(ByteString(crateContent)).runWith(sink))
      .map { response =>
        fail(s"Received unexpected response from endpoint: [$response]")
      }
      .recover { case NonFatal(e) =>
        e.getMessage should startWith(
          s"Endpoint [http://localhost:$endpointPort] was unable to reserve enough storage for request"
        )
        endpoint.fixtures.crateStore.statistics(MockCrateStore.Statistic.PersistCompleted) should be(0)
        endpoint.fixtures.crateStore.statistics(MockCrateStore.Statistic.PersistFailed) should be(0)
      }
  }

  it should "handle reservation failures" in {
    val endpointPort = ports.dequeue()
    val endpointAddress = HttpEndpointAddress(s"http://localhost:$endpointPort/invalid")

    val endpoint = createTestHttpEndpoint(port = endpointPort)

    val client = HttpEndpointClient(
      credentials = new MockHttpNodeCredentialsProvider(endpointAddress, testUser, testPassword),
      maxChunkSize = maxChunkSize,
      config = PoolClient.Config.Default
    )

    client
      .push(endpointAddress, testManifest)
      .flatMap(sink => Source.single(ByteString(crateContent)).runWith(sink))
      .map { response =>
        fail(s"Received unexpected response from endpoint: [$response]")
      }
      .recover { case NonFatal(e) =>
        e.getMessage should be(
          s"Endpoint [http://localhost:$endpointPort/invalid] responded to storage request with unexpected status: [404 Not Found]"
        )
        endpoint.fixtures.crateStore.statistics(MockCrateStore.Statistic.PersistCompleted) should be(0)
        endpoint.fixtures.crateStore.statistics(MockCrateStore.Statistic.PersistFailed) should be(0)
      }
  }

  it should "handle push failures via a stream sink" in {
    val endpointPort = ports.dequeue()
    val endpointAddress = HttpEndpointAddress(s"http://localhost:$endpointPort")

    implicit val telemetry: MockTelemetryContext = MockTelemetryContext()

    val endpoint = createTestHttpEndpoint(
      port = endpointPort,
      fixtures = Some(
        new TestFixtures {
          override lazy val crateStore: MockCrateStore = new MockCrateStore(persistDisabled = true)
        }
      )
    )

    val client = HttpEndpointClient(
      credentials = new MockHttpNodeCredentialsProvider(endpointAddress, testUser, testPassword),
      maxChunkSize = maxChunkSize,
      config = PoolClient.Config.Default.copy(minBackoff = 10.millis, maxBackoff = 100.millis, maxRetries = 2)
    )

    client
      .push(endpointAddress, testManifest)
      .flatMap(sink => Source.single(ByteString(crateContent)).runWith(sink))
      .map { response =>
        fail(s"Received unexpected response from endpoint: [$response]")
      }
      .recover { case NonFatal(e) =>
        e.getMessage should be(
          s"Endpoint [http://localhost:$endpointPort] responded to push for crate [${testManifest.crate}] " +
            s"with unexpected status: [500 Internal Server Error]"
        )
        endpoint.fixtures.crateStore.statistics(MockCrateStore.Statistic.PersistCompleted) should be(0)
        endpoint.fixtures.crateStore.statistics(MockCrateStore.Statistic.PersistFailed) should be(3)
      }
  }

  it should "successfully pull crates" in {
    val endpointPort = ports.dequeue()
    val endpointAddress = HttpEndpointAddress(s"http://localhost:$endpointPort")

    val endpoint = createTestHttpEndpoint(port = endpointPort)

    val client = HttpEndpointClient(
      credentials = new MockHttpNodeCredentialsProvider(endpointAddress, testUser, testPassword),
      maxChunkSize = maxChunkSize,
      config = PoolClient.Config.Default
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

  it should "handle trying to pull missing crates" in {
    val endpointPort = ports.dequeue()
    val endpointAddress = HttpEndpointAddress(s"http://localhost:$endpointPort")

    val _ = createTestHttpEndpoint(port = endpointPort)

    val client = HttpEndpointClient(
      credentials = new MockHttpNodeCredentialsProvider(endpointAddress, testUser, testPassword),
      maxChunkSize = maxChunkSize,
      config = PoolClient.Config.Default
    )

    client.pull(endpointAddress, Crate.generateId()).map { response =>
      response should be(None)
    }
  }

  it should "handle pull failures" in {
    val endpointPort = ports.dequeue()
    val endpointAddress = HttpEndpointAddress(s"http://localhost:$endpointPort")

    val _ = createTestHttpEndpoint(port = endpointPort)

    val client = HttpEndpointClient(
      credentials = new MockHttpNodeCredentialsProvider(endpointAddress, "invalid-user", testPassword),
      maxChunkSize = maxChunkSize,
      config = PoolClient.Config.Default
    )

    val crate = Crate.generateId()

    client
      .pull(endpointAddress, crate)
      .map { response =>
        fail(s"Received unexpected response from endpoint: [$response]")
      }
      .recover { case NonFatal(e) =>
        e.getMessage should be(
          s"Endpoint [http://localhost:$endpointPort] responded to pull for crate [$crate] " +
            s"with unexpected status: [401 Unauthorized]"
        )
      }
  }

  it should "be able to authenticate against multiple endpoints" in {
    val primaryEndpointPort = ports.dequeue()
    val primaryEndpointAddress = HttpEndpointAddress(s"http://localhost:$primaryEndpointPort")
    val secondaryEndpointPort = ports.dequeue()
    val secondaryEndpointAddress = HttpEndpointAddress(s"http://localhost:$secondaryEndpointPort")

    val primaryEndpointUser = "primary-endpoint-user"
    val primaryEndpointPassword = "primary-endpoint-password"
    val secondaryEndpointUser = "secondary-endpoint-user"
    val secondaryEndpointPassword = "secondary-endpoint-password"

    createTestHttpEndpoint(
      testAuthenticator = new MockHttpAuthenticator(primaryEndpointUser, primaryEndpointPassword),
      port = primaryEndpointPort
    )

    createTestHttpEndpoint(
      testAuthenticator = new MockHttpAuthenticator(secondaryEndpointUser, secondaryEndpointPassword),
      port = secondaryEndpointPort
    )

    val client = HttpEndpointClient(
      credentials = new MockHttpNodeCredentialsProvider(
        Map(
          primaryEndpointAddress -> (primaryEndpointUser, primaryEndpointPassword),
          secondaryEndpointAddress -> (secondaryEndpointUser, secondaryEndpointPassword)
        )
      ),
      maxChunkSize = maxChunkSize,
      config = PoolClient.Config.Default
    )

    def pushWith(address: HttpEndpointAddress, ec: ExecutionContext): Future[Done] = client
      .push(address, testManifest)
      .flatMap(Source.single(ByteString(crateContent)).runWith(_))(ec)

    for {
      _ <- pushWith(primaryEndpointAddress, typedSystem.executionContext)
      _ <- pushWith(secondaryEndpointAddress, typedSystem.executionContext)
    } yield {
      succeed
    }
  }

  it should "fail to push crates via sink if no credentials are available" in {
    val endpointPort = ports.dequeue()
    val endpointAddress = HttpEndpointAddress(s"http://localhost:$endpointPort")

    val _ = createTestHttpEndpoint(port = endpointPort)

    val client = HttpEndpointClient(
      credentials = new MockHttpNodeCredentialsProvider(Map.empty),
      maxChunkSize = maxChunkSize,
      config = PoolClient.Config.Default
    )

    client
      .push(endpointAddress, testManifest)
      .map { response =>
        fail(s"Received unexpected response from endpoint: [$response]")
      }
      .recover { case NonFatal(e) =>
        e.getMessage should be(
          s"Push to endpoint [${endpointAddress.uri}] via sink failed for crate [${testManifest.crate}]; " +
            s"unable to retrieve credentials: [No credentials found for [HttpEndpointAddress(http://localhost:$endpointPort)]]"
        )
      }
  }

  it should "fail to pull crates if no credentials are available" in {
    val endpointPort = ports.dequeue()
    val endpointAddress = HttpEndpointAddress(s"http://localhost:$endpointPort")

    val _ = createTestHttpEndpoint(port = endpointPort)

    val client = HttpEndpointClient(
      credentials = new MockHttpNodeCredentialsProvider(Map.empty),
      maxChunkSize = maxChunkSize,
      config = PoolClient.Config.Default
    )

    val crateId = Crate.generateId()

    client
      .pull(endpointAddress, crateId)
      .map { response =>
        fail(s"Received unexpected response from endpoint: [$response]")
      }
      .recover { case NonFatal(e) =>
        e.getMessage should be(
          s"Pull from endpoint [${endpointAddress.uri}] failed for crate [$crateId]; " +
            s"unable to retrieve credentials: [No credentials found for [HttpEndpointAddress(http://localhost:$endpointPort)]]"
        )
      }
  }

  it should "successfully discard existing crates" in {
    val endpointPort = ports.dequeue()
    val endpointAddress = HttpEndpointAddress(s"http://localhost:$endpointPort")

    val endpoint = createTestHttpEndpoint(port = endpointPort)

    val client = HttpEndpointClient(
      credentials = new MockHttpNodeCredentialsProvider(endpointAddress, testUser, testPassword),
      maxChunkSize = maxChunkSize,
      config = PoolClient.Config.Default
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

  it should "fail to discard crates if no credentials are available" in {
    val endpointPort = ports.dequeue()
    val endpointAddress = HttpEndpointAddress(s"http://localhost:$endpointPort")

    val _ = createTestHttpEndpoint(port = endpointPort)

    val client = HttpEndpointClient(
      credentials = new MockHttpNodeCredentialsProvider(Map.empty),
      maxChunkSize = maxChunkSize,
      config = PoolClient.Config.Default
    )

    val crateId = Crate.generateId()

    client
      .discard(endpointAddress, crateId)
      .map { response =>
        fail(s"Received unexpected response from endpoint: [$response]")
      }
      .recover { case NonFatal(e) =>
        e.getMessage should be(
          s"Discard from endpoint [${endpointAddress.uri}] failed for crate [$crateId]; " +
            s"unable to retrieve credentials: [No credentials found for [HttpEndpointAddress(http://localhost:$endpointPort)]]"
        )
      }
  }

  it should "fail to discard missing crates" in {
    val endpointPort = ports.dequeue()
    val endpointAddress = HttpEndpointAddress(s"http://localhost:$endpointPort")

    val _ = createTestHttpEndpoint(port = endpointPort)

    val client = HttpEndpointClient(
      credentials = new MockHttpNodeCredentialsProvider(endpointAddress, testUser, testPassword),
      maxChunkSize = maxChunkSize,
      config = PoolClient.Config.Default
    )

    client.discard(endpointAddress, Crate.generateId()).map { result =>
      result should be(false)
    }
  }

  it should "handle discard failures" in {
    val endpointPort = ports.dequeue()
    val endpointAddress = HttpEndpointAddress(s"http://localhost:$endpointPort")

    val _ = createTestHttpEndpoint(port = endpointPort)

    val client = HttpEndpointClient(
      credentials = new MockHttpNodeCredentialsProvider(endpointAddress, "invalid-user", testPassword),
      maxChunkSize = maxChunkSize,
      config = PoolClient.Config.Default
    )

    val crate = Crate.generateId()

    client
      .discard(endpointAddress, crate)
      .map { response =>
        fail(s"Received unexpected response from endpoint: [$response]")
      }
      .recover { case NonFatal(e) =>
        e.getMessage should be(
          s"Endpoint [${endpointAddress.uri}] responded to discard for crate [$crate] " +
            s"with unexpected status: [401 Unauthorized]"
        )
      }
  }

  it should "support custom connection contexts" in {
    val endpointPort = ports.dequeue()
    val endpointAddress = HttpEndpointAddress(s"https://localhost:$endpointPort")

    val config: Config = ConfigFactory.load().getConfig("stasis.test.core.security.tls")

    val endpointContext = EndpointContext(
      config = EndpointContext.Config(config.getConfig("context-server"))
    )

    val clientContext = EndpointContext(
      config = EndpointContext.Config(config.getConfig("context-client"))
    )

    val endpoint = createTestHttpEndpoint(port = endpointPort, context = Some(endpointContext))

    val client = HttpEndpointClient(
      credentials = new MockHttpNodeCredentialsProvider(endpointAddress, testUser, testPassword),
      context = clientContext,
      maxChunkSize = maxChunkSize,
      config = PoolClient.Config.Default
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
    "HttpEndpointClientSpec_Typed"
  )

  private val crateContent = "some value"

  private val testManifest = Manifest(
    crate = Crate.generateId(),
    size = 1,
    copies = 7,
    source = Node.generateId(),
    origin = Node.generateId()
  )

  private val testUser = "test-user"
  private val testPassword = "test-password"

  private def createTestHttpEndpoint(
    testAuthenticator: NodeAuthenticator[HttpCredentials] = new MockHttpAuthenticator(testUser, testPassword),
    fixtures: Option[TestFixtures] = None,
    context: Option[EndpointContext] = None,
    port: Int
  ): TestHttpEndpoint = {
    implicit val telemetry: MockTelemetryContext = MockTelemetryContext()

    new TestHttpEndpoint(
      testAuthenticator = testAuthenticator,
      fixtures = fixtures.getOrElse(new TestFixtures),
      context = context,
      port = port
    )
  }

  private class TestFixtures(implicit telemetry: TelemetryContext) {
    lazy val reservationStore: MockReservationStore = MockReservationStore()
    lazy val crateStore: MockCrateStore = new MockCrateStore(maxStorageSize = Some(99))
    lazy val router: MockRouter = new MockRouter(crateStore, Node.generateId(), reservationStore)
  }

  private class TestHttpEndpoint(
    val testAuthenticator: NodeAuthenticator[HttpCredentials],
    val fixtures: TestFixtures,
    context: Option[EndpointContext],
    port: Int
  )(implicit telemetry: TelemetryContext)
      extends HttpEndpoint(
        router = fixtures.router,
        authenticator = testAuthenticator,
        reservationStore = fixtures.reservationStore.view
      ) {
    locally {
      val _ = start(interface = "localhost", port = port, context = context)
    }
  }

  private val ports: mutable.Queue[Int] = (19000 to 19100).to(mutable.Queue)

  private val maxChunkSize = 100
}
