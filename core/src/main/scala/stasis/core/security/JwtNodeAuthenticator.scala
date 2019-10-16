package stasis.core.security

import java.util.UUID

import akka.Done
import akka.http.scaladsl.model.headers.{HttpCredentials, OAuth2BearerToken}
import org.jose4j.jwt.JwtClaims
import stasis.core.persistence.nodes.NodeStoreView
import stasis.core.routing.Node
import stasis.core.security.exceptions.AuthenticationFailure
import stasis.core.security.jwt.JwtAuthenticator

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Try}

class JwtNodeAuthenticator(
  nodeStore: NodeStoreView,
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
            case false => Future.failed(AuthenticationFailure(s"Node [$node] not found"))
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
        node <- Try(UUID.fromString(identity)).recoverWith {
          case _: IllegalArgumentException =>
            Failure(AuthenticationFailure(s"Invalid node ID encountered: [$identity]"))
        }
      } yield {
        node
      }
    )
}
