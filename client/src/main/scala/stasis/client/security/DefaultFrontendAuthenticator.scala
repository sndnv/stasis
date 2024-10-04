package stasis.client.security

import java.util.concurrent.ThreadLocalRandom

import scala.concurrent.Future
import scala.util.Random

import org.apache.pekko.Done
import org.apache.pekko.http.scaladsl.model.headers.HttpCredentials
import org.apache.pekko.http.scaladsl.model.headers.OAuth2BearerToken

import stasis.layers.security.exceptions.AuthenticationFailure

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
