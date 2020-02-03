package stasis.client.security

import java.util.concurrent.ThreadLocalRandom

import akka.Done
import akka.http.scaladsl.model.headers.{HttpCredentials, OAuth2BearerToken}
import stasis.core.security.exceptions.AuthenticationFailure

import scala.concurrent.Future
import scala.util.Random

class DefaultFrontendAuthenticator(val token: String) extends FrontendAuthenticator {
  override def authenticate(credentials: HttpCredentials): Future[Done] =
    credentials match {
      case OAuth2BearerToken(`token`) =>
        Future.successful(Done)

      case OAuth2BearerToken(_) =>
        Future.failed(AuthenticationFailure("Invalid credentials provided"))

      case _ =>
        Future.failed(AuthenticationFailure(s"Unsupported credentials provided: [${credentials.scheme()}]"))
    }
}

object DefaultFrontendAuthenticator {
  def apply(tokenSize: Int): DefaultFrontendAuthenticator =
    new DefaultFrontendAuthenticator(token = generateToken(withSize = tokenSize))

  def generateToken(withSize: Int): String = {
    val rnd: Random = ThreadLocalRandom.current()
    rnd.alphanumeric.take(withSize).mkString
  }
}
