package stasis.server.security

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

import org.apache.pekko.http.scaladsl.model.headers.HttpCredentials
import org.apache.pekko.http.scaladsl.model.headers.OAuth2BearerToken

import io.github.sndnv.layers.security.jwt.JwtProvider

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
