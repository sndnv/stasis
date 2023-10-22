package stasis.server.security

import org.apache.pekko.http.scaladsl.model.headers.{HttpCredentials, OAuth2BearerToken}
import stasis.core.security.jwt.JwtProvider

import scala.concurrent.{ExecutionContext, Future}

trait CredentialsProvider {
  def provide(): Future[HttpCredentials]
}

object CredentialsProvider {
  class Default(
    scope: String,
    underlying: JwtProvider
  )(implicit ec: ExecutionContext)
      extends CredentialsProvider {
    override def provide(): Future[HttpCredentials] =
      underlying.provide(scope = scope).map(OAuth2BearerToken)
  }

  object Default {
    def apply(scope: String, underlying: JwtProvider)(implicit ec: ExecutionContext): Default =
      new Default(scope, underlying)
  }
}
