package stasis.core.security

import java.util.UUID

import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.util.Failure
import scala.util.Success
import scala.util.Try

import stasis.core.persistence.nodes.NodeStore
import stasis.core.routing.Node
import io.github.sndnv.layers.persistence.KeyValueStore
import io.github.sndnv.layers.security.exceptions.AuthenticationFailure

class PreSharedKeyNodeAuthenticator(
  nodeStore: NodeStore.View,
  backend: KeyValueStore[String, String]
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

      case Failure(_) =>
        Future.failed(AuthenticationFailure(s"Invalid node ID encountered: [$node]"))
    }
  }
}
