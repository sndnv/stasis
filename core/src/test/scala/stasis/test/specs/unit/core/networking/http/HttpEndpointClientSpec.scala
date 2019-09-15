package stasis.test.specs.unit.core.networking.http

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorSystem, Behavior, SpawnProtocol}
import akka.http.scaladsl.ConnectionContext
import akka.http.scaladsl.model.headers.HttpCredentials
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.Source
import akka.util.ByteString
import org.scalatest.concurrent.Eventually
import stasis.core.networking.http.{HttpEndpoint, HttpEndpointAddress, HttpEndpointClient}
import stasis.core.packaging.{Crate, Manifest}
import stasis.core.routing.Node
import stasis.core.security.NodeAuthenticator
import stasis.test.specs.unit.AsyncUnitSpec
import stasis.test.specs.unit.core.networking.mocks.MockHttpNodeCredentialsProvider
import stasis.test.specs.unit.core.persistence.mocks.{MockCrateStore, MockReservationStore}
import stasis.test.specs.unit.core.routing.mocks.MockRouter
import stasis.test.specs.unit.core.security.mocks.MockHttpAuthenticator

import scala.collection.mutable
import scala.util.control.NonFatal

class HttpEndpointClientSpec extends AsyncUnitSpec with Eventually {

  private implicit val untypedSystem: akka.actor.ActorSystem =
    akka.actor.ActorSystem(name = "HttpEndpointClientSpec_Untyped")

  private implicit val typedSystem: ActorSystem[SpawnProtocol] = ActorSystem(
    Behaviors.setup(_ => SpawnProtocol.behavior): Behavior[SpawnProtocol],
    "HttpEndpointClientSpec_Typed"
  )

  private implicit val mat: ActorMaterializer = ActorMaterializer()

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

  private trait TestFixtures {
    lazy val reservationStore: MockReservationStore = new MockReservationStore()
    lazy val crateStore: MockCrateStore = new MockCrateStore(reservationStore, maxReservationSize = Some(99))
    lazy val router: MockRouter = new MockRouter(crateStore)
  }

  private class TestHttpEndpoint(
    val testAuthenticator: NodeAuthenticator[HttpCredentials] = new MockHttpAuthenticator(testUser, testPassword),
    val fixtures: TestFixtures = new TestFixtures {},
    port: Int
  ) extends HttpEndpoint(
        router = fixtures.router,
        authenticator = testAuthenticator,
        reservationStore = fixtures.reservationStore.view
      ) {
    private val _ = start(interface = "localhost", port = port, context = ConnectionContext.noEncryption())
  }

  private val ports: mutable.Queue[Int] = (19000 to 19100).to[mutable.Queue]

  "An HTTP Endpoint Client" should "successfully push crates" in {
    val endpointPort = ports.dequeue()
    val endpointAddress = HttpEndpointAddress(s"http://localhost:$endpointPort")

    val endpoint = new TestHttpEndpoint(port = endpointPort)

    val client = new HttpEndpointClient(
      credentials = new MockHttpNodeCredentialsProvider(endpointAddress, testUser, testPassword)
    )

    client.push(endpointAddress, testManifest, Source.single(ByteString(crateContent))).map { _ =>
      endpoint.fixtures.crateStore.statistics(MockCrateStore.Statistic.PersistCompleted) should be(1)
      endpoint.fixtures.crateStore.statistics(MockCrateStore.Statistic.PersistFailed) should be(0)
      endpoint.fixtures.crateStore.statistics(MockCrateStore.Statistic.ReserveCompleted) should be(1)
      endpoint.fixtures.crateStore.statistics(MockCrateStore.Statistic.ReserveLimited) should be(0)
      endpoint.fixtures.crateStore.statistics(MockCrateStore.Statistic.ReserveFailed) should be(0)
    }
  }

  it should "handle reservation rejections" in {
    val endpointPort = ports.dequeue()
    val endpointAddress = HttpEndpointAddress(s"http://localhost:$endpointPort")

    val endpoint = new TestHttpEndpoint(port = endpointPort)

    val client = new HttpEndpointClient(
      credentials = new MockHttpNodeCredentialsProvider(endpointAddress, testUser, testPassword)
    )

    client
      .push(endpointAddress, testManifest.copy(size = 100), Source.single(ByteString(crateContent)))
      .map { response =>
        fail(s"Received unexpected response from endpoint: [$response]")
      }
      .recover {
        case NonFatal(e) =>
          e.getMessage should startWith(
            s"Endpoint [http://localhost:$endpointPort] was unable to reserve enough storage for request"
          )
          endpoint.fixtures.crateStore.statistics(MockCrateStore.Statistic.PersistCompleted) should be(0)
          endpoint.fixtures.crateStore.statistics(MockCrateStore.Statistic.PersistFailed) should be(0)
          endpoint.fixtures.crateStore.statistics(MockCrateStore.Statistic.ReserveCompleted) should be(0)
          endpoint.fixtures.crateStore.statistics(MockCrateStore.Statistic.ReserveLimited) should be(1)
          endpoint.fixtures.crateStore.statistics(MockCrateStore.Statistic.ReserveFailed) should be(0)
      }
  }

  it should "handle reservation failures" in {
    val endpointPort = ports.dequeue()
    val endpointAddress = HttpEndpointAddress(s"http://localhost:$endpointPort/invalid")

    val endpoint = new TestHttpEndpoint(port = endpointPort)

    val client = new HttpEndpointClient(
      credentials = new MockHttpNodeCredentialsProvider(endpointAddress, testUser, testPassword)
    )

    client
      .push(endpointAddress, testManifest, Source.single(ByteString(crateContent)))
      .map { response =>
        fail(s"Received unexpected response from endpoint: [$response]")
      }
      .recover {
        case NonFatal(e) =>
          e.getMessage should be(
            s"Endpoint [http://localhost:$endpointPort/invalid] responded to storage request with unexpected status: [404 Not Found]"
          )
          endpoint.fixtures.crateStore.statistics(MockCrateStore.Statistic.PersistCompleted) should be(0)
          endpoint.fixtures.crateStore.statistics(MockCrateStore.Statistic.PersistFailed) should be(0)
          endpoint.fixtures.crateStore.statistics(MockCrateStore.Statistic.ReserveCompleted) should be(0)
          endpoint.fixtures.crateStore.statistics(MockCrateStore.Statistic.ReserveLimited) should be(0)
          endpoint.fixtures.crateStore.statistics(MockCrateStore.Statistic.ReserveFailed) should be(0)
      }
  }

  it should "handle push failures" in {
    val endpointPort = ports.dequeue()
    val endpointAddress = HttpEndpointAddress(s"http://localhost:$endpointPort")

    val endpoint = new TestHttpEndpoint(
      port = endpointPort,
      fixtures = new TestFixtures {
        override lazy val crateStore: MockCrateStore = new MockCrateStore(
          reservationStore,
          persistDisabled = true
        )
      }
    )

    val client = new HttpEndpointClient(
      credentials = new MockHttpNodeCredentialsProvider(endpointAddress, testUser, testPassword)
    )

    client
      .push(endpointAddress, testManifest, Source.single(ByteString(crateContent)))
      .map { response =>
        fail(s"Received unexpected response from endpoint: [$response]")
      }
      .recover {
        case NonFatal(e) =>
          e.getMessage should be(
            s"Endpoint [http://localhost:$endpointPort] responded to push with unexpected status: [500 Internal Server Error]"
          )
          endpoint.fixtures.crateStore.statistics(MockCrateStore.Statistic.PersistCompleted) should be(0)
          endpoint.fixtures.crateStore.statistics(MockCrateStore.Statistic.PersistFailed) should be(1)
          endpoint.fixtures.crateStore.statistics(MockCrateStore.Statistic.ReserveCompleted) should be(1)
          endpoint.fixtures.crateStore.statistics(MockCrateStore.Statistic.ReserveLimited) should be(0)
          endpoint.fixtures.crateStore.statistics(MockCrateStore.Statistic.ReserveFailed) should be(0)
      }
  }

  it should "successfully push crates via a stream sink" in {
    val endpointPort = ports.dequeue()
    val endpointAddress = HttpEndpointAddress(s"http://localhost:$endpointPort")

    val endpoint = new TestHttpEndpoint(port = endpointPort)

    val client = new HttpEndpointClient(
      credentials = new MockHttpNodeCredentialsProvider(endpointAddress, testUser, testPassword)
    )

    client.sink(endpointAddress, testManifest).flatMap { sink =>
      Source
        .single(ByteString(crateContent))
        .runWith(sink)
        .map { _ =>
          eventually {
            endpoint.fixtures.crateStore.statistics(MockCrateStore.Statistic.PersistCompleted) should be(1)
            endpoint.fixtures.crateStore.statistics(MockCrateStore.Statistic.PersistFailed) should be(0)
            endpoint.fixtures.crateStore.statistics(MockCrateStore.Statistic.ReserveCompleted) should be(1)
            endpoint.fixtures.crateStore.statistics(MockCrateStore.Statistic.ReserveLimited) should be(0)
            endpoint.fixtures.crateStore.statistics(MockCrateStore.Statistic.ReserveFailed) should be(0)
          }
        }
    }
  }

  it should "successfully pull crates" in {
    val endpointPort = ports.dequeue()
    val endpointAddress = HttpEndpointAddress(s"http://localhost:$endpointPort")

    val endpoint = new TestHttpEndpoint(port = endpointPort)

    val client = new HttpEndpointClient(
      credentials = new MockHttpNodeCredentialsProvider(endpointAddress, testUser, testPassword)
    )

    client.push(endpointAddress, testManifest, Source.single(ByteString(crateContent))).flatMap { _ =>
      client.pull(endpointAddress, testManifest.crate).flatMap {
        case Some(source) =>
          source
            .runFold(ByteString.empty) {
              case (folded, chunk) =>
                folded.concat(chunk)
            }
            .map { result =>
              result.utf8String should be(crateContent)
              endpoint.fixtures.crateStore.statistics(MockCrateStore.Statistic.PersistCompleted) should be(1)
              endpoint.fixtures.crateStore.statistics(MockCrateStore.Statistic.PersistFailed) should be(0)
              endpoint.fixtures.crateStore.statistics(MockCrateStore.Statistic.ReserveCompleted) should be(1)
              endpoint.fixtures.crateStore.statistics(MockCrateStore.Statistic.ReserveLimited) should be(0)
              endpoint.fixtures.crateStore.statistics(MockCrateStore.Statistic.ReserveFailed) should be(0)
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

    val _ = new TestHttpEndpoint(port = endpointPort)

    val client = new HttpEndpointClient(
      credentials = new MockHttpNodeCredentialsProvider(endpointAddress, testUser, testPassword)
    )

    client.pull(endpointAddress, Crate.generateId()).map { response =>
      response should be(None)
    }
  }

  it should "handle pull failures" in {
    val endpointPort = ports.dequeue()
    val endpointAddress = HttpEndpointAddress(s"http://localhost:$endpointPort")

    val _ = new TestHttpEndpoint(port = endpointPort)

    val client = new HttpEndpointClient(
      credentials = new MockHttpNodeCredentialsProvider(endpointAddress, "invalid-user", testPassword)
    )

    client
      .pull(endpointAddress, Crate.generateId())
      .map { response =>
        fail(s"Received unexpected response from endpoint: [$response]")
      }
      .recover {
        case NonFatal(e) =>
          e.getMessage should be(
            s"Endpoint [http://localhost:$endpointPort] responded to pull with unexpected status: [401 Unauthorized]"
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

    new TestHttpEndpoint(
      testAuthenticator = new MockHttpAuthenticator(primaryEndpointUser, primaryEndpointPassword),
      port = primaryEndpointPort
    )

    new TestHttpEndpoint(
      testAuthenticator = new MockHttpAuthenticator(secondaryEndpointUser, secondaryEndpointPassword),
      port = secondaryEndpointPort
    )

    val client = new HttpEndpointClient(
      credentials = new MockHttpNodeCredentialsProvider(
        Map(
          primaryEndpointAddress -> (primaryEndpointUser, primaryEndpointPassword),
          secondaryEndpointAddress -> (secondaryEndpointUser, secondaryEndpointPassword)
        )
      )
    )

    for {
      _ <- client.push(primaryEndpointAddress, testManifest, Source.single(ByteString(crateContent)))
      _ <- client.push(secondaryEndpointAddress, testManifest, Source.single(ByteString(crateContent)))
    } yield {
      succeed
    }
  }

  it should "fail to push crates if no credentials are available" in {
    val endpointPort = ports.dequeue()
    val endpointAddress = HttpEndpointAddress(s"http://localhost:$endpointPort")

    val _ = new TestHttpEndpoint(port = endpointPort)

    val client = new HttpEndpointClient(
      credentials = new MockHttpNodeCredentialsProvider(Map.empty)
    )

    client
      .push(endpointAddress, testManifest, Source.single(ByteString(crateContent)))
      .map { response =>
        fail(s"Received unexpected response from endpoint: [$response]")
      }
      .recover {
        case NonFatal(e) =>
          e.getMessage should be(
            s"Push to endpoint [${endpointAddress.uri}] failed for crate [${testManifest.crate}]; " +
              s"unable to retrieve credentials: [No credentials found for [HttpEndpointAddress(http://localhost:$endpointPort)]]"
          )
      }
  }

  it should "fail to push crates via sink if no credentials are available" in {
    val endpointPort = ports.dequeue()
    val endpointAddress = HttpEndpointAddress(s"http://localhost:$endpointPort")

    val _ = new TestHttpEndpoint(port = endpointPort)

    val client = new HttpEndpointClient(
      credentials = new MockHttpNodeCredentialsProvider(Map.empty)
    )

    client
      .sink(endpointAddress, testManifest)
      .map { response =>
        fail(s"Received unexpected response from endpoint: [$response]")
      }
      .recover {
        case NonFatal(e) =>
          e.getMessage should be(
            s"Push to endpoint [${endpointAddress.uri}] via sink failed for crate [${testManifest.crate}]; " +
              s"unable to retrieve credentials: [No credentials found for [HttpEndpointAddress(http://localhost:$endpointPort)]]"
          )
      }
  }

  it should "fail to pull crates if no credentials are available" in {
    val endpointPort = ports.dequeue()
    val endpointAddress = HttpEndpointAddress(s"http://localhost:$endpointPort")

    val _ = new TestHttpEndpoint(port = endpointPort)

    val client = new HttpEndpointClient(
      credentials = new MockHttpNodeCredentialsProvider(Map.empty)
    )

    val crateId = Crate.generateId()

    client
      .pull(endpointAddress, crateId)
      .map { response =>
        fail(s"Received unexpected response from endpoint: [$response]")
      }
      .recover {
        case NonFatal(e) =>
          e.getMessage should be(
            s"Pull from endpoint [${endpointAddress.uri}] failed for crate [$crateId]; " +
              s"unable to retrieve credentials: [No credentials found for [HttpEndpointAddress(http://localhost:$endpointPort)]]"
          )
      }
  }

  it should "successfully discard existing crates" in {
    val endpointPort = ports.dequeue()
    val endpointAddress = HttpEndpointAddress(s"http://localhost:$endpointPort")

    val endpoint = new TestHttpEndpoint(port = endpointPort)

    val client = new HttpEndpointClient(
      credentials = new MockHttpNodeCredentialsProvider(endpointAddress, testUser, testPassword)
    )

    client.push(endpointAddress, testManifest, Source.single(ByteString(crateContent))).flatMap { _ =>
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

    val _ = new TestHttpEndpoint(port = endpointPort)

    val client = new HttpEndpointClient(
      credentials = new MockHttpNodeCredentialsProvider(Map.empty)
    )

    val crateId = Crate.generateId()

    client
      .discard(endpointAddress, crateId)
      .map { response =>
        fail(s"Received unexpected response from endpoint: [$response]")
      }
      .recover {
        case NonFatal(e) =>
          e.getMessage should be(
            s"Discard from endpoint [${endpointAddress.uri}] failed for crate [$crateId]; " +
              s"unable to retrieve credentials: [No credentials found for [HttpEndpointAddress(http://localhost:$endpointPort)]]"
          )
      }
  }

  it should "fail to discard missing crates" in {
    val endpointPort = ports.dequeue()
    val endpointAddress = HttpEndpointAddress(s"http://localhost:$endpointPort")

    val _ = new TestHttpEndpoint(port = endpointPort)

    val client = new HttpEndpointClient(
      credentials = new MockHttpNodeCredentialsProvider(endpointAddress, testUser, testPassword)
    )

    client.discard(endpointAddress, Crate.generateId()).map { result =>
      result should be(false)
    }
  }

  it should "handle discard failures" in {
    val endpointPort = ports.dequeue()
    val endpointAddress = HttpEndpointAddress(s"http://localhost:$endpointPort")

    val _ = new TestHttpEndpoint(port = endpointPort)

    val client = new HttpEndpointClient(
      credentials = new MockHttpNodeCredentialsProvider(endpointAddress, "invalid-user", testPassword)
    )

    client
      .discard(endpointAddress, Crate.generateId())
      .map { response =>
        fail(s"Received unexpected response from endpoint: [$response]")
      }
      .recover {
        case NonFatal(e) =>
          e.getMessage should be(
            s"Endpoint [${endpointAddress.uri}] responded to discard with unexpected status: [401 Unauthorized]"
          )
      }
  }
}
