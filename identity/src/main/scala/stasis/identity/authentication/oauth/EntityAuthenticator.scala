package stasis.identity.authentication.oauth

import akka.actor.ActorSystem
import akka.http.scaladsl.model.headers.BasicHttpCredentials
import stasis.core.security.exceptions.AuthenticationFailure
import stasis.identity.model.secrets.Secret

import scala.concurrent.{ExecutionContext, Future}

trait EntityAuthenticator[T] {

  protected implicit def system: ActorSystem
  protected implicit def config: Secret.Config

  protected implicit val ec: ExecutionContext = system.dispatcher

  protected def getEntity(username: String): Future[T]
  protected def extractSecret: T => Secret
  protected def extractSalt: T => String

  def authenticate(credentials: BasicHttpCredentials): Future[T] =
    getEntity(credentials.username).flatMap { entity =>
      val secret = extractSecret(entity)
      val salt = extractSalt(entity)

      val result =
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

      akka.pattern.after(
        duration = config.authenticationDelay,
        using = system.scheduler
      )(result)
    }
}
