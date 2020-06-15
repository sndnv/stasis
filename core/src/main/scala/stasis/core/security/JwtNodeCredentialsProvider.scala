package stasis.core.security

import akka.http.scaladsl.model.headers.{HttpCredentials, OAuth2BearerToken}
import stasis.core.networking.EndpointAddress
import stasis.core.persistence.nodes.NodeStoreView
import stasis.core.routing.Node
import stasis.core.security.exceptions.ProviderFailure
import stasis.core.security.jwt.JwtProvider

import scala.concurrent.{ExecutionContext, Future}

class JwtNodeCredentialsProvider[A <: EndpointAddress](
  nodeStore: NodeStoreView,
  underlying: JwtProvider
)(implicit ec: ExecutionContext)
    extends NodeCredentialsProvider[A, HttpCredentials] {

  override def provide(address: A): Future[HttpCredentials] =
    nodeStore.nodes
      .flatMap { nodes =>
        nodes.collectFirst {
          case (_, node: Node.Remote[A]) if node.address == address => node.id
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
    nodeStore: NodeStoreView,
    underlying: JwtProvider
  )(implicit ec: ExecutionContext): JwtNodeCredentialsProvider[A] =
    new JwtNodeCredentialsProvider[A](
      nodeStore = nodeStore,
      underlying = underlying
    )
}
