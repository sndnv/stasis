package stasis.core.security.jwt

import org.jose4j.jwt.JwtClaims

import scala.concurrent.Future

trait JwtAuthenticator {
  def identityClaim: String
  def authenticate(credentials: String): Future[JwtClaims]
}
