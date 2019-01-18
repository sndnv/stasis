package stasis.security.psk

import java.util.UUID

import stasis.persistence.backends.KeyValueBackend
import stasis.routing.Node
import stasis.security.NodeAuthenticator
import stasis.security.exceptions.AuthenticationFailure

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}

class PreSharedKeyAuthenticator(
  backend: KeyValueBackend[String, String]
)(implicit ec: ExecutionContext)
    extends NodeAuthenticator[(String, String)] {
  override def authenticate(credentials: (String, String)): Future[Node.Id] = {
    val (node, secret) = credentials

    Try(UUID.fromString(node)) match {
      case Success(nodeId) =>
        backend.get(node).flatMap {
          case Some(`secret`) => Future.successful(nodeId)
          case Some(_)        => Future.failed(AuthenticationFailure(s"Invalid secret supplied for node [$node]"))
          case None           => Future.failed(AuthenticationFailure(s"Node [$node] was not found"))
        }

      case Failure(e) =>
        Future.failed(AuthenticationFailure(s"Invalid node ID encountered: [$node]"))
    }
  }
}
