package stasis.client.api.clients

import org.apache.pekko.actor.typed.{ActorSystem, SpawnProtocol}
import org.apache.pekko.http.scaladsl.model.headers.HttpCredentials
import org.apache.pekko.stream.scaladsl.Source
import org.apache.pekko.util.ByteString
import org.apache.pekko.{Done, NotUsed}
import stasis.core.networking.http.{HttpEndpointAddress, HttpEndpointClient}
import stasis.core.packaging
import stasis.core.routing.Node
import stasis.core.security.tls.EndpointContext
import scala.concurrent.Future

import stasis.core.api.PoolClient

class DefaultServerCoreEndpointClient(
  address: HttpEndpointAddress,
  credentials: => Future[HttpCredentials],
  override val self: Node.Id,
  context: Option[EndpointContext],
  maxChunkSize: Int,
  config: PoolClient.Config
)(implicit system: ActorSystem[SpawnProtocol.Command])
    extends ServerCoreEndpointClient {

  private val client: HttpEndpointClient = HttpEndpointClient(
    credentials = (_: HttpEndpointAddress) => credentials,
    context = context,
    maxChunkSize = maxChunkSize,
    config = config
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
    context: Option[EndpointContext],
    maxChunkSize: Int,
    config: PoolClient.Config
  )(implicit system: ActorSystem[SpawnProtocol.Command]): DefaultServerCoreEndpointClient =
    new DefaultServerCoreEndpointClient(
      address = address,
      credentials = credentials,
      self = self,
      context = context,
      maxChunkSize = maxChunkSize,
      config = config
    )
}
