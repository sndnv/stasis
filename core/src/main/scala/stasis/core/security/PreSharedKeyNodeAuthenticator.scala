package stasis.core.security

import java.util.UUID

import stasis.core.persistence.backends.KeyValueBackend
import stasis.core.persistence.nodes.NodeStoreView
import stasis.core.routing.Node
import stasis.core.security.exceptions.AuthenticationFailure

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}

class PreSharedKeyNodeAuthenticator(
  nodeStore: NodeStoreView,
  backend: KeyValueBackend[String, String]
)(implicit ec: ExecutionContext)
    extends NodeAuthenticator[(String, String)] {
  override def authenticate(credentials: (String, String)): Future[Node.Id] = {
    val (node, secret) = credentials

    Try(UUID.fromString(node)) match {
      case Success(nodeId) =>
        nodeStore.contains(nodeId).flatMap { nodeExists =>
          if (nodeExists) {
            backend.get(node).flatMap {
              case Some(`secret`) => Future.successful(nodeId)
              case Some(_)        => Future.failed(AuthenticationFailure(s"Invalid secret supplied for node [$node]"))
              case None           => Future.failed(AuthenticationFailure(s"Credentials for node [$node] not found"))
            }
          } else {
            Future.failed(AuthenticationFailure(s"Node [$node] not found"))
          }
        }

      case Failure(e) =>
        Future.failed(AuthenticationFailure(s"Invalid node ID encountered: [$node]"))
    }
  }
}
