package stasis.test.specs.unit.client.api.clients

import org.apache.pekko.actor.typed.scaladsl.Behaviors
import org.apache.pekko.actor.typed.{ActorSystem, Behavior, SpawnProtocol}
import org.apache.pekko.http.scaladsl.model.headers.BasicHttpCredentials
import org.apache.pekko.stream.scaladsl.Source
import org.apache.pekko.util.{ByteString, Timeout}
import com.typesafe.config.{Config, ConfigFactory}
import org.scalatest.Assertion
import org.scalatest.concurrent.Eventually
import stasis.client.api.clients.DefaultServerCoreEndpointClient
import stasis.core.networking.http.HttpEndpointAddress
import stasis.core.packaging.{Crate, Manifest}
import stasis.core.routing.Node
import stasis.core.routing.exceptions.PullFailure
import stasis.core.security.tls.EndpointContext
import stasis.test.specs.unit.AsyncUnitSpec
import stasis.test.specs.unit.client.mocks.MockServerCoreEndpoint
import stasis.test.specs.unit.core.telemetry.MockTelemetryContext

import scala.collection.mutable
import scala.concurrent.Future
import scala.concurrent.duration._

class DefaultServerCoreEndpointClientSpec extends AsyncUnitSpec with Eventually {
  "A DefaultServerCoreEndpointClient" should "push crates" in {
    val coreCredentials = BasicHttpCredentials(username = "some-user", password = "some-password")
    val corePort = ports.dequeue()
    val core = new MockServerCoreEndpoint(expectedCredentials = coreCredentials)
    core.start(port = corePort)

    val coreClient = new DefaultServerCoreEndpointClient(
      address = HttpEndpointAddress(uri = s"http://localhost:$corePort"),
      credentials = Future.successful(coreCredentials),
      self = Node.generateId(),
      context = None,
      requestBufferSize = 100,
      maxChunkSize = 100
    )

    val crateId = Crate.generateId()
    val crateContent = ByteString("some-crate")

    val _ = coreClient
      .push(
        manifest = Manifest(
          crate = crateId,
          origin = Node.generateId(),
          source = Node.generateId(),
          size = crateContent.size.toLong,
          copies = 1
        ),
        content = Source.single(crateContent)
      )
      .await

    eventually[Assertion] {
      core.crateExists(crateId).await should be(true)
    }
  }

  it should "pull crates" in {
    val coreCredentials = BasicHttpCredentials(username = "some-user", password = "some-password")
    val corePort = ports.dequeue()
    val core = new MockServerCoreEndpoint(expectedCredentials = coreCredentials)
    core.start(port = corePort)

    val coreClient = new DefaultServerCoreEndpointClient(
      address = HttpEndpointAddress(uri = s"http://localhost:$corePort"),
      credentials = Future.successful(coreCredentials),
      self = Node.generateId(),
      context = None,
      requestBufferSize = 100,
      maxChunkSize = 100
    )

    val crateId = Crate.generateId()
    val crateContent = ByteString("some-crate")

    for {
      _ <- core.insertCrate(
        manifest = Manifest(
          crate = crateId,
          origin = Node.generateId(),
          source = Node.generateId(),
          size = crateContent.size.toLong,
          copies = 1
        ),
        content = Source.single(crateContent)
      )
      actualContent <- coreClient.pull(crateId).flatMap {
        case Some(content) => content.runFold(ByteString.empty)(_ concat _)
        case None          => Future.failed(PullFailure(s"Unexpected response received; content for crate [$crateId] missing"))

      }
    } yield {
      actualContent should be(crateContent)
    }
  }

  it should "support custom connection contexts" in {
    val coreCredentials = BasicHttpCredentials(username = "some-user", password = "some-password")
    val corePort = ports.dequeue()

    val config: Config = ConfigFactory.load().getConfig("stasis.test.client.security.tls")

    val endpointContext = EndpointContext(
      config = EndpointContext.Config(config.getConfig("context-server"))
    )

    val clientContext = EndpointContext(
      config = EndpointContext.Config(config.getConfig("context-client"))
    )

    val core = new MockServerCoreEndpoint(expectedCredentials = coreCredentials)

    val coreClient = new DefaultServerCoreEndpointClient(
      address = HttpEndpointAddress(uri = s"https://localhost:$corePort"),
      credentials = Future.successful(coreCredentials),
      self = Node.generateId(),
      context = Some(clientContext),
      requestBufferSize = 100,
      maxChunkSize = 100
    )

    core.start(port = corePort, context = Some(endpointContext))

    val crateId = Crate.generateId()
    val crateContent = ByteString("some-crate")

    val _ = coreClient
      .push(
        manifest = Manifest(
          crate = crateId,
          origin = Node.generateId(),
          source = Node.generateId(),
          size = crateContent.size.toLong,
          copies = 1
        ),
        content = Source.single(crateContent)
      )
      .await

    eventually[Assertion] {
      core.crateExists(crateId).await should be(true)
    }
  }

  private implicit val typedSystem: ActorSystem[SpawnProtocol.Command] = ActorSystem(
    Behaviors.setup(_ => SpawnProtocol()): Behavior[SpawnProtocol.Command],
    "DefaultServerCoreEndpointClientSpec"
  )

  private implicit val telemetry: MockTelemetryContext = MockTelemetryContext()

  override implicit val patienceConfig: PatienceConfig = PatienceConfig(5.seconds, 250.milliseconds)

  private val ports: mutable.Queue[Int] = (23000 to 23100).to(mutable.Queue)

  override implicit val timeout: Timeout = 3.seconds
}
