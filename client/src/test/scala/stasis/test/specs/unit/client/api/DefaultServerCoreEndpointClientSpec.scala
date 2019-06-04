package stasis.test.specs.unit.client.api

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.scaladsl.adapter._
import akka.actor.typed.{ActorSystem, Behavior, SpawnProtocol}
import akka.http.scaladsl.model.headers.BasicHttpCredentials
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.Source
import akka.util.ByteString
import stasis.client.api.DefaultServerCoreEndpointClient
import stasis.core.networking.http.HttpEndpointAddress
import stasis.core.packaging.{Crate, Manifest}
import stasis.core.routing.Node
import stasis.core.routing.exceptions.PullFailure
import stasis.test.specs.unit.AsyncUnitSpec
import stasis.test.specs.unit.client.mocks.MockServerCoreEndpoint

import scala.collection.mutable
import scala.concurrent.Future

class DefaultServerCoreEndpointClientSpec extends AsyncUnitSpec {
  private implicit val typedSystem: ActorSystem[SpawnProtocol] = ActorSystem(
    Behaviors.setup(_ => SpawnProtocol.behavior): Behavior[SpawnProtocol],
    "DefaultServerCoreEndpointClientSpec"
  )

  private implicit val untypedSystem: akka.actor.ActorSystem = typedSystem.toUntyped

  private implicit val mat: ActorMaterializer = ActorMaterializer()

  private val ports: mutable.Queue[Int] = (23000 to 23100).to[mutable.Queue]

  "A DefaultServerCoreEndpointClient" should "push crates" in {
    val coreCredentials = BasicHttpCredentials(username = "some-user", password = "some-password")
    val corePort = ports.dequeue()
    val core = new MockServerCoreEndpoint(expectedCredentials = coreCredentials)
    core.start(port = corePort)

    val coreClient = new DefaultServerCoreEndpointClient(
      coreAddress = HttpEndpointAddress(uri = s"http://localhost:$corePort"),
      coreCredentials = coreCredentials,
      self = Node.generateId()
    )

    val crateId = Crate.generateId()
    val crateContent = ByteString("some-crate")

    for {
      _ <- coreClient.push(
        manifest = Manifest(
          crate = crateId,
          origin = Node.generateId(),
          source = Node.generateId(),
          size = crateContent.size,
          copies = 1
        ),
        content = Source.single(crateContent)
      )
      crateExists <- core.crateExists(crateId)
    } yield {
      crateExists should be(true)
    }
  }

  it should "pull crates" in {
    val coreCredentials = BasicHttpCredentials(username = "some-user", password = "some-password")
    val corePort = ports.dequeue()
    val core = new MockServerCoreEndpoint(expectedCredentials = coreCredentials)
    core.start(port = corePort)

    val coreClient = new DefaultServerCoreEndpointClient(
      coreAddress = HttpEndpointAddress(uri = s"http://localhost:$corePort"),
      coreCredentials = coreCredentials,
      self = Node.generateId()
    )

    val crateId = Crate.generateId()
    val crateContent = ByteString("some-crate")

    for {
      _ <- core.insertCrate(
        manifest = Manifest(
          crate = crateId,
          origin = Node.generateId(),
          source = Node.generateId(),
          size = crateContent.size,
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
}
