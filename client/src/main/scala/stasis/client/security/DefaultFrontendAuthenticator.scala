package stasis.client.security

import akka.http.scaladsl.model.headers.{HttpCredentials, OAuth2BearerToken}
import akka.Done
import stasis.core.security.exceptions.AuthenticationFailure
import stasis.core.security.jwt.JwtAuthenticator

import scala.concurrent.{ExecutionContext, Future}

class DefaultFrontendAuthenticator(
  underlying: JwtAuthenticator
)(implicit ec: ExecutionContext)
    extends FrontendAuthenticator {
  override def authenticate(credentials: HttpCredentials): Future[Done] =
    credentials match {
      case OAuth2BearerToken(token) =>
        underlying.authenticate(credentials = token).map(_ => Done)

      case _ =>
        Future.failed(AuthenticationFailure(s"Unsupported credentials provided: [${credentials.scheme()}]"))
    }
}
