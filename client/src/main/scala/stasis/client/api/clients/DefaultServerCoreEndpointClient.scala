package stasis.client.api.clients

import scala.concurrent.Future

import org.apache.pekko.Done
import org.apache.pekko.NotUsed
import org.apache.pekko.actor.typed.ActorSystem
import org.apache.pekko.http.scaladsl.model.headers.HttpCredentials
import org.apache.pekko.stream.scaladsl.Source
import org.apache.pekko.util.ByteString

import stasis.core.api.PoolClient
import stasis.core.networking.http.HttpEndpointAddress
import stasis.core.networking.http.HttpEndpointClient
import stasis.core.packaging
import stasis.core.routing.Node
import io.github.sndnv.layers.security.tls.EndpointContext

class DefaultServerCoreEndpointClient(
  address: HttpEndpointAddress,
  credentials: => Future[HttpCredentials],
  override val self: Node.Id,
  context: Option[EndpointContext],
  maxChunkSize: Int,
  config: PoolClient.Config
)(implicit system: ActorSystem[Nothing])
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
  )(implicit system: ActorSystem[Nothing]): DefaultServerCoreEndpointClient =
    new DefaultServerCoreEndpointClient(
      address = address,
      credentials = credentials,
      self = self,
      context = context,
      maxChunkSize = maxChunkSize,
      config = config
    )
}
