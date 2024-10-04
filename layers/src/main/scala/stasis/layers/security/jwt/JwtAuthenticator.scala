package stasis.layers.security.jwt

import scala.concurrent.Future

import org.jose4j.jwt.JwtClaims

trait JwtAuthenticator {
  def identityClaim: String
  def authenticate(credentials: String): Future[JwtClaims]
}
