package stasis.client.api

import akka.actor.typed.{ActorSystem, SpawnProtocol}
import akka.http.scaladsl.model.headers.HttpCredentials
import akka.stream.scaladsl.Source
import akka.util.ByteString
import akka.{Done, NotUsed}
import stasis.core.networking.http.{HttpEndpointAddress, HttpEndpointClient}
import stasis.core.packaging
import stasis.core.routing.Node

import scala.concurrent.Future

class DefaultServerCoreEndpointClient(
  coreAddress: HttpEndpointAddress,
  coreCredentials: HttpCredentials,
  override val self: Node.Id
)(implicit system: ActorSystem[SpawnProtocol])
    extends ServerCoreEndpointClient {
  private val client: HttpEndpointClient = HttpEndpointClient(_ => Future.successful(coreCredentials))

  override def push(manifest: packaging.Manifest, content: Source[ByteString, NotUsed]): Future[Done] =
    client.push(coreAddress, manifest, content)

  override def pull(crate: Node.Id): Future[Option[Source[ByteString, NotUsed]]] =
    client.pull(coreAddress, crate)
}
