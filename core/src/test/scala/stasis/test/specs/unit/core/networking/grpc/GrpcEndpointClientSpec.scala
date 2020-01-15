package stasis.test.specs.unit.core.networking.grpc

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorSystem, Behavior, SpawnProtocol}
import akka.http.scaladsl.ConnectionContext
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.Source
import akka.util.ByteString
import com.typesafe.config.{Config, ConfigFactory}
import org.scalatest.concurrent.Eventually
import stasis.core.networking.grpc.{GrpcEndpoint, GrpcEndpointAddress, GrpcEndpointClient}
import stasis.core.packaging.{Crate, Manifest}
import stasis.core.routing.Node
import stasis.core.security.tls.EndpointContext
import stasis.test.specs.unit.AsyncUnitSpec
import stasis.test.specs.unit.core.networking.mocks.MockGrpcNodeCredentialsProvider
import stasis.test.specs.unit.core.persistence.mocks.{MockCrateStore, MockReservationStore}
import stasis.test.specs.unit.core.routing.mocks.MockRouter
import stasis.test.specs.unit.core.security.mocks.MockGrpcAuthenticator

import scala.collection.mutable
import scala.concurrent.duration._
import scala.util.control.NonFatal

class GrpcEndpointClientSpec extends AsyncUnitSpec with Eventually {
  "An GRPC Endpoint Client" should "successfully push crates" in {
    val endpointPort = ports.dequeue()
    val endpointAddress = GrpcEndpointAddress("localhost", endpointPort, tlsEnabled = false)

    val endpoint = new TestGrpcEndpoint(port = endpointPort)

    val client = GrpcEndpointClient(
      credentials = new MockGrpcNodeCredentialsProvider(endpointAddress, testNode, testSecret)
    )

    client.push(endpointAddress, testManifest, Source.single(ByteString(crateContent))).map { _ =>
      endpoint.fixtures.crateStore.statistics(MockCrateStore.Statistic.PersistCompleted) should be(1)
      endpoint.fixtures.crateStore.statistics(MockCrateStore.Statistic.PersistFailed) should be(0)
    }
  }

  it should "handle reservation rejections" in {
    val endpointPort = ports.dequeue()
    val endpointAddress = GrpcEndpointAddress("localhost", endpointPort, tlsEnabled = false)

    val endpoint = new TestGrpcEndpoint(port = endpointPort)

    val client = GrpcEndpointClient(
      credentials = new MockGrpcNodeCredentialsProvider(endpointAddress, testNode, testSecret)
    )

    client
      .push(endpointAddress, testManifest.copy(size = 100), Source.single(ByteString(crateContent)))
      .map { response =>
        fail(s"Received unexpected response from endpoint: [$response]")
      }
      .recover {
        case NonFatal(e) =>
          e.getMessage should be(
            s"Reservation on endpoint [${endpointAddress.host}] failed for crate [${testManifest.crate}]: " +
              s"[Reservation rejected for node [$testNode]]"
          )
          endpoint.fixtures.crateStore.statistics(MockCrateStore.Statistic.PersistCompleted) should be(0)
          endpoint.fixtures.crateStore.statistics(MockCrateStore.Statistic.PersistFailed) should be(0)
      }
  }

  it should "handle push failures" in {
    val endpointPort = ports.dequeue()
    val endpointAddress = GrpcEndpointAddress("localhost", endpointPort, tlsEnabled = false)

    val endpoint = new TestGrpcEndpoint(
      port = endpointPort,
      fixtures = new TestFixtures {
        override lazy val crateStore: MockCrateStore = new MockCrateStore(persistDisabled = true)
      }
    )

    val client = GrpcEndpointClient(
      credentials = new MockGrpcNodeCredentialsProvider(endpointAddress, testNode, testSecret)
    )

    client
      .push(endpointAddress, testManifest, Source.single(ByteString(crateContent)))
      .map { response =>
        fail(s"Received unexpected response from endpoint: [$response]")
      }
      .recover {
        case NonFatal(e) =>
          e.getMessage should be(
            s"Push to endpoint [${endpointAddress.host}] failed for crate [${testManifest.crate}]: " +
              s"[Push failed for node [$testNode]: [[persistDisabled] is set to [true]]]"
          )
          endpoint.fixtures.crateStore.statistics(MockCrateStore.Statistic.PersistCompleted) should be(0)
          endpoint.fixtures.crateStore.statistics(MockCrateStore.Statistic.PersistFailed) should be(1)
      }
  }

  it should "successfully push crates via a stream sink" in {
    val endpointPort = ports.dequeue()
    val endpointAddress = GrpcEndpointAddress("localhost", endpointPort, tlsEnabled = false)

    val endpoint = new TestGrpcEndpoint(port = endpointPort)

    val client = GrpcEndpointClient(
      credentials = new MockGrpcNodeCredentialsProvider(endpointAddress, testNode, testSecret)
    )

    client.sink(endpointAddress, testManifest).flatMap { sink =>
      Source
        .single(ByteString(crateContent))
        .runWith(sink)
        .map { _ =>
          eventually {
            endpoint.fixtures.crateStore.statistics(MockCrateStore.Statistic.PersistCompleted) should be(1)
            endpoint.fixtures.crateStore.statistics(MockCrateStore.Statistic.PersistFailed) should be(0)
          }
        }
    }
  }

  it should "successfully pull crates" in {
    val endpointPort = ports.dequeue()
    val endpointAddress = GrpcEndpointAddress("localhost", endpointPort, tlsEnabled = false)

    val endpoint = new TestGrpcEndpoint(port = endpointPort)

    val client = GrpcEndpointClient(
      credentials = new MockGrpcNodeCredentialsProvider(endpointAddress, testNode, testSecret)
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
    val endpointAddress = GrpcEndpointAddress("localhost", endpointPort, tlsEnabled = false)

    val _ = new TestGrpcEndpoint(port = endpointPort)

    val client = GrpcEndpointClient(
      credentials = new MockGrpcNodeCredentialsProvider(endpointAddress, testNode, testSecret)
    )

    client.pull(endpointAddress, Crate.generateId()).map { response =>
      response should be(None)
    }
  }

  it should "handle pull failures" in {
    val endpointPort = ports.dequeue()
    val endpointAddress = GrpcEndpointAddress("localhost", endpointPort, tlsEnabled = false)

    val _ = new TestGrpcEndpoint(port = endpointPort)

    val client = GrpcEndpointClient(
      credentials = new MockGrpcNodeCredentialsProvider(endpointAddress, testNode, "invalid-secret")
    )

    val crateId = Crate.generateId()

    client
      .pull(endpointAddress, crateId)
      .map { response =>
        fail(s"Received unexpected response from endpoint: [$response]")
      }
      .recover {
        case NonFatal(e) =>
          e.getMessage should startWith(s"Pull from endpoint [${endpointAddress.host}] failed for crate [$crateId]")
      }
  }

  it should "be able to authenticate against multiple endpoints" in {
    val primaryEndpointPort = ports.dequeue()
    val primaryEndpointAddress = GrpcEndpointAddress("localhost", primaryEndpointPort, tlsEnabled = false)
    val secondaryEndpointPort = ports.dequeue()
    val secondaryEndpointAddress = GrpcEndpointAddress("localhost", secondaryEndpointPort, tlsEnabled = false)

    val primaryEndpointNode = Node.generateId()
    val primaryEndpointSecret = "primary-endpoint-secret"
    val secondaryEndpointNode = Node.generateId()
    val secondaryEndpointSecret = "secondary-endpoint-secret"

    new TestGrpcEndpoint(
      testAuthenticator = new MockGrpcAuthenticator(primaryEndpointNode, primaryEndpointSecret),
      port = primaryEndpointPort
    )

    new TestGrpcEndpoint(
      testAuthenticator = new MockGrpcAuthenticator(secondaryEndpointNode, secondaryEndpointSecret),
      port = secondaryEndpointPort
    )

    val client = GrpcEndpointClient(
      credentials = new MockGrpcNodeCredentialsProvider(
        Map(
          primaryEndpointAddress -> (primaryEndpointNode, primaryEndpointSecret),
          secondaryEndpointAddress -> (secondaryEndpointNode, secondaryEndpointSecret)
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
    val endpointAddress = GrpcEndpointAddress("localhost", endpointPort, tlsEnabled = false)

    val _ = new TestGrpcEndpoint(port = endpointPort)

    val client = GrpcEndpointClient(
      credentials = new MockGrpcNodeCredentialsProvider(Map.empty)
    )

    client
      .push(endpointAddress, testManifest, Source.single(ByteString(crateContent)))
      .map { response =>
        fail(s"Received unexpected response from endpoint: [$response]")
      }
      .recover {
        case NonFatal(e) =>
          e.getMessage should be(
            s"Push to endpoint [${endpointAddress.host}] failed for crate [${testManifest.crate}]; " +
              s"unable to retrieve credentials: [No credentials found for [GrpcEndpointAddress(localhost,$endpointPort,false)]]"
          )
      }
  }

  it should "fail to push crates via sink if no credentials are available" in {
    val endpointPort = ports.dequeue()
    val endpointAddress = GrpcEndpointAddress("localhost", endpointPort, tlsEnabled = false)

    val _ = new TestGrpcEndpoint(port = endpointPort)

    val client = GrpcEndpointClient(
      credentials = new MockGrpcNodeCredentialsProvider(Map.empty)
    )

    client
      .sink(endpointAddress, testManifest)
      .map { response =>
        fail(s"Received unexpected response from endpoint: [$response]")
      }
      .recover {
        case NonFatal(e) =>
          e.getMessage should be(
            s"Push to endpoint [${endpointAddress.host}] via sink failed for crate [${testManifest.crate}]; " +
              s"unable to retrieve credentials: [No credentials found for [GrpcEndpointAddress(localhost,$endpointPort,false)]]"
          )
      }
  }

  it should "fail to pull crates if no credentials are available" in {
    val endpointPort = ports.dequeue()
    val endpointAddress = GrpcEndpointAddress("localhost", endpointPort, tlsEnabled = false)

    val _ = new TestGrpcEndpoint(port = endpointPort)

    val client = GrpcEndpointClient(
      credentials = new MockGrpcNodeCredentialsProvider(Map.empty)
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
            s"Pull from endpoint [${endpointAddress.host}] failed for crate [$crateId]; " +
              s"unable to retrieve credentials: [No credentials found for [GrpcEndpointAddress(localhost,$endpointPort,false)]]"
          )
      }
  }

  it should "successfully discard existing crates" in {
    val endpointPort = ports.dequeue()
    val endpointAddress = GrpcEndpointAddress("localhost", endpointPort, tlsEnabled = false)

    val endpoint = new TestGrpcEndpoint(port = endpointPort)

    val client = GrpcEndpointClient(
      credentials = new MockGrpcNodeCredentialsProvider(endpointAddress, testNode, testSecret)
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
    val endpointAddress = GrpcEndpointAddress("localhost", endpointPort, tlsEnabled = false)

    val _ = new TestGrpcEndpoint(port = endpointPort)

    val client = GrpcEndpointClient(
      credentials = new MockGrpcNodeCredentialsProvider(Map.empty)
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
            s"Discard from endpoint [${endpointAddress.host}] failed for crate [$crateId]; " +
              s"unable to retrieve credentials: [No credentials found for [GrpcEndpointAddress(localhost,$endpointPort,false)]]"
          )
      }
  }

  it should "fail to discard missing crates" in {
    val endpointPort = ports.dequeue()
    val endpointAddress = GrpcEndpointAddress("localhost", endpointPort, tlsEnabled = false)

    val _ = new TestGrpcEndpoint(port = endpointPort)

    val client = GrpcEndpointClient(
      credentials = new MockGrpcNodeCredentialsProvider(endpointAddress, testNode, testSecret)
    )

    client.discard(endpointAddress, Crate.generateId()).map { result =>
      result should be(false)
    }
  }

  it should "handle discard failures" in {
    val endpointPort = ports.dequeue()
    val endpointAddress = GrpcEndpointAddress("localhost", endpointPort, tlsEnabled = false)

    val _ = new TestGrpcEndpoint(port = endpointPort)

    val client = GrpcEndpointClient(
      credentials = new MockGrpcNodeCredentialsProvider(endpointAddress, testNode, "invalid-secret")
    )

    client.discard(endpointAddress, Crate.generateId()).map { result =>
      result should be(false)
    }
  }

  it should "support custom connection contexts" in {
    val endpointPort = ports.dequeue()
    val endpointAddress = GrpcEndpointAddress("localhost", endpointPort, tlsEnabled = true)

    val config: Config = ConfigFactory.load().getConfig("stasis.test.core.security.tls")

    val endpointContext = EndpointContext.create(
      contextConfig = EndpointContext.ContextConfig(config.getConfig("context-server"))
    )

    val clientContext = EndpointContext.create(
      contextConfig = EndpointContext.ContextConfig(config.getConfig("context-client"))
    )

    val endpoint = new TestGrpcEndpoint(port = endpointPort, context = endpointContext)

    val client = GrpcEndpointClient(
      credentials = new MockGrpcNodeCredentialsProvider(endpointAddress, testNode, testSecret),
      context = clientContext
    )

    client.push(endpointAddress, testManifest, Source.single(ByteString(crateContent))).map { _ =>
      endpoint.fixtures.crateStore.statistics(MockCrateStore.Statistic.PersistCompleted) should be(1)
      endpoint.fixtures.crateStore.statistics(MockCrateStore.Statistic.PersistFailed) should be(0)
    }
  }

  override implicit val patienceConfig: PatienceConfig = PatienceConfig(5.seconds, 250.milliseconds)

  private implicit val untypedSystem: akka.actor.ActorSystem =
    akka.actor.ActorSystem(name = "GrpcEndpointClientSpec_Untyped")

  private implicit val typedSystem: ActorSystem[SpawnProtocol] = ActorSystem(
    Behaviors.setup(_ => SpawnProtocol.behavior): Behavior[SpawnProtocol],
    "GrpcEndpointClientSpec_Typed"
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

  private val testNode = Node.generateId()
  private val testSecret = "test-secret"

  private trait TestFixtures {
    lazy val reservationStore: MockReservationStore = new MockReservationStore()
    lazy val crateStore: MockCrateStore = new MockCrateStore(maxStorageSize = Some(99))
    lazy val router: MockRouter = new MockRouter(crateStore, testNode, reservationStore)
  }

  private class TestGrpcEndpoint(
    val testAuthenticator: MockGrpcAuthenticator = new MockGrpcAuthenticator(testNode, testSecret),
    val fixtures: TestFixtures = new TestFixtures {},
    context: ConnectionContext = ConnectionContext.noEncryption(),
    port: Int
  ) extends GrpcEndpoint(fixtures.router, fixtures.reservationStore.view, testAuthenticator) {
    private val _ = start("localhost", port, connectionContext = context)
  }

  private val ports: mutable.Queue[Int] = (20000 to 20100).to[mutable.Queue]
}
