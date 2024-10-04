package stasis.identity.authentication.oauth

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

import org.apache.pekko.actor.typed.ActorSystem
import org.apache.pekko.http.scaladsl.model.headers.BasicHttpCredentials
import stasis.identity.model.secrets.Secret
import stasis.layers.security.exceptions.AuthenticationFailure

trait EntityAuthenticator[T] {

  protected implicit def system: ActorSystem[Nothing]
  protected implicit def config: Secret.Config

  protected implicit val ec: ExecutionContext = system.executionContext

  protected def getEntity(username: String): Future[T]
  protected def extractSecret: T => Secret
  protected def extractSalt: T => String

  def authenticate(credentials: BasicHttpCredentials): Future[T] = {
    val result = getEntity(credentials.username).flatMap { entity =>
      val secret = extractSecret(entity)
      val salt = extractSalt(entity)

      Future(secret.isSameAs(credentials.password, salt))
        .flatMap { credentialsMatch =>
          if (credentialsMatch) {
            Future.successful(entity)
          } else {
            Future.failed(
              AuthenticationFailure(s"Credentials for entity [${credentials.username}] do not match")
            )
          }
        }

    }

    org.apache.pekko.pattern.after(
      duration = config.authenticationDelay,
      using = system.classicSystem.scheduler
    )(result)
  }
}
