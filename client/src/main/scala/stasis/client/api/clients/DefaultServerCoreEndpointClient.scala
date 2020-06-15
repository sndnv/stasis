package stasis.client.api.clients

import akka.actor.typed.{ActorSystem, SpawnProtocol}
import akka.http.scaladsl.model.headers.HttpCredentials
import akka.http.scaladsl.{Http, HttpsConnectionContext}
import akka.stream.scaladsl.Source
import akka.util.ByteString
import akka.{Done, NotUsed}
import stasis.core.networking.http.{HttpEndpointAddress, HttpEndpointClient}
import stasis.core.packaging
import stasis.core.routing.Node

import scala.concurrent.Future

class DefaultServerCoreEndpointClient(
  address: HttpEndpointAddress,
  credentials: => Future[HttpCredentials],
  override val self: Node.Id,
  context: Option[HttpsConnectionContext],
  requestBufferSize: Int
)(implicit system: ActorSystem[SpawnProtocol.Command])
    extends ServerCoreEndpointClient {

  private implicit val untypedSystem: akka.actor.ActorSystem = system.classicSystem

  private val clientContext: HttpsConnectionContext = context match {
    case Some(context) => context
    case None          => Http().defaultClientHttpsContext
  }

  private val client: HttpEndpointClient = HttpEndpointClient(
    credentials = (_: HttpEndpointAddress) => credentials,
    context = clientContext,
    requestBufferSize = requestBufferSize
  )

  override val server: String = address.uri.toString

  override def push(manifest: packaging.Manifest, content: Source[ByteString, NotUsed]): Future[Done] =
    client.push(address, manifest, content)

  override def pull(crate: Node.Id): Future[Option[Source[ByteString, NotUsed]]] =
    client.pull(address, crate)
}

object DefaultServerCoreEndpointClient {
  def apply(
    address: HttpEndpointAddress,
    credentials: => Future[HttpCredentials],
    self: Node.Id,
    context: Option[HttpsConnectionContext],
    requestBufferSize: Int
  )(implicit system: ActorSystem[SpawnProtocol.Command]): DefaultServerCoreEndpointClient =
    new DefaultServerCoreEndpointClient(
      address = address,
      credentials = credentials,
      self = self,
      context = context,
      requestBufferSize = requestBufferSize
    )
}
