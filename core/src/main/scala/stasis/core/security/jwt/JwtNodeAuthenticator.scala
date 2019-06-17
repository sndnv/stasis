package stasis.core.security.jwt

import java.util.UUID

import org.jose4j.jwt.JwtClaims
import stasis.core.routing.Node
import stasis.core.security.NodeAuthenticator
import stasis.core.security.exceptions.AuthenticationFailure

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Try}

class JwtNodeAuthenticator(underlying: JwtAuthenticator)(implicit ec: ExecutionContext)
    extends NodeAuthenticator[String] {

  override def authenticate(credentials: String): Future[Node.Id] =
    for {
      claims <- underlying.authenticate(credentials)
      node <- extractNodeFromClaims(claims)
    } yield {
      node
    }

  private def extractNodeFromClaims(claims: JwtClaims): Future[Node.Id] =
    Future.fromTry(
      for {
        subject <- Try(claims.getSubject)
        node <- Try(UUID.fromString(subject)).recoverWith {
          case _: IllegalArgumentException =>
            Failure(AuthenticationFailure(s"Invalid node ID encountered: [$subject]"))
        }
      } yield {
        node
      }
    )
}
