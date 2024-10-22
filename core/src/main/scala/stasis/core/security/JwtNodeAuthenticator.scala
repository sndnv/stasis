package stasis.core.security

import java.util.UUID

import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.util.Failure
import scala.util.Try

import org.apache.pekko.Done
import org.apache.pekko.http.scaladsl.model.headers.HttpCredentials
import org.apache.pekko.http.scaladsl.model.headers.OAuth2BearerToken
import org.jose4j.jwt.JwtClaims

import stasis.core.persistence.nodes.NodeStore
import stasis.core.routing.Node
import stasis.layers.security.exceptions.AuthenticationFailure
import stasis.layers.security.jwt.JwtAuthenticator

class JwtNodeAuthenticator(
  nodeStore: NodeStore.View,
  underlying: JwtAuthenticator
)(implicit ec: ExecutionContext)
    extends NodeAuthenticator[HttpCredentials] {

  override def authenticate(credentials: HttpCredentials): Future[Node.Id] =
    credentials match {
      case OAuth2BearerToken(token) =>
        for {
          claims <- underlying.authenticate(token)
          node <- extractNodeFromClaims(claims)
          _ <- nodeStore.contains(node).flatMap {
            case true  => Future.successful(Done)
            case false => Future.failed(AuthenticationFailure(s"Node [${node.toString}] not found"))
          }
        } yield {
          node
        }

      case _ =>
        Future.failed(AuthenticationFailure(s"Unsupported node credentials provided: [${credentials.scheme()}]"))
    }

  private def extractNodeFromClaims(claims: JwtClaims): Future[Node.Id] =
    Future.fromTry(
      for {
        identity <- Try(claims.getClaimValue(underlying.identityClaim, classOf[String]))
        node <- Try(UUID.fromString(identity)).recoverWith { case _: IllegalArgumentException =>
          Failure(AuthenticationFailure(s"Invalid node ID encountered: [$identity]"))
        }
      } yield {
        node
      }
    )
}

object JwtNodeAuthenticator {
  def apply(
    nodeStore: NodeStore.View,
    underlying: JwtAuthenticator
  )(implicit ec: ExecutionContext): JwtNodeAuthenticator =
    new JwtNodeAuthenticator(
      nodeStore = nodeStore,
      underlying = underlying
    )
}
