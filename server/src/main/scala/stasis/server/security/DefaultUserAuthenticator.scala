package stasis.server.security
import java.util.UUID

import akka.http.scaladsl.model.headers.{HttpCredentials, OAuth2BearerToken}
import org.jose4j.jwt.JwtClaims
import stasis.core.security.exceptions.AuthenticationFailure
import stasis.core.security.jwt.JwtAuthenticator
import stasis.server.model.users.UserStore
import stasis.shared.model.users.User

import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try

class DefaultUserAuthenticator(
  store: UserStore.View.Privileged,
  underlying: JwtAuthenticator
)(implicit ec: ExecutionContext)
    extends UserAuthenticator {
  override def authenticate(credentials: HttpCredentials): Future[CurrentUser] =
    credentials match {
      case OAuth2BearerToken(token) =>
        for {
          claims <- underlying.authenticate(token)
          user <- extractUserFromClaims(claims)
        } yield {
          CurrentUser(user.id)
        }

      case _ =>
        Future.failed(AuthenticationFailure(s"Unsupported user credentials provided: [${credentials.scheme()}]"))
    }

  private def extractUserFromClaims(claims: JwtClaims): Future[User] =
    for {
      identity <- Future
        .fromTry(Try(claims.getClaimValue(underlying.identityClaim, classOf[String])))
        .map(UUID.fromString)
      userOpt <- store.get(identity)
      user <- userOpt match {
        case Some(user) if user.active => Future.successful(user)
        case Some(_)                   => Future.failed(AuthenticationFailure(s"User [${identity.toString}] is not active"))
        case None                      => Future.failed(AuthenticationFailure(s"User [${identity.toString}] not found"))
      }
    } yield {
      user
    }
}

object DefaultUserAuthenticator {
  def apply(
    store: UserStore.View.Privileged,
    underlying: JwtAuthenticator
  )(implicit ec: ExecutionContext): DefaultUserAuthenticator =
    new DefaultUserAuthenticator(
      store = store,
      underlying = underlying
    )
}
