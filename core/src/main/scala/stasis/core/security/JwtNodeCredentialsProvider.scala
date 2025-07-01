package stasis.core.security

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

import org.apache.pekko.http.scaladsl.model.headers.HttpCredentials
import org.apache.pekko.http.scaladsl.model.headers.OAuth2BearerToken

import stasis.core.networking.EndpointAddress
import stasis.core.persistence.nodes.NodeStore
import stasis.core.routing.Node
import io.github.sndnv.layers.security.exceptions.ProviderFailure
import io.github.sndnv.layers.security.jwt.JwtProvider

class JwtNodeCredentialsProvider[A <: EndpointAddress](
  nodeStore: NodeStore.View,
  underlying: JwtProvider
)(implicit ec: ExecutionContext)
    extends NodeCredentialsProvider[A, HttpCredentials] {

  override def provide(address: A): Future[HttpCredentials] =
    nodeStore.nodes
      .flatMap { nodes =>
        nodes.collectFirst {
          case (_, node: Node.Remote[_]) if node.address == address => node.id
        } match {
          case Some(node) => Future.successful(node)
          case None       => Future.failed(ProviderFailure(s"Failed to find node with address [${address.toString}]"))
        }
      }
      .flatMap { node =>
        underlying.provide(scope = node.toString).map(OAuth2BearerToken)
      }

}

object JwtNodeCredentialsProvider {
  def apply[A <: EndpointAddress](
    nodeStore: NodeStore.View,
    underlying: JwtProvider
  )(implicit ec: ExecutionContext): JwtNodeCredentialsProvider[A] =
    new JwtNodeCredentialsProvider[A](
      nodeStore = nodeStore,
      underlying = underlying
    )
}
