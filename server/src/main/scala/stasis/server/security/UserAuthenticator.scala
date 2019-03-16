package stasis.server.security

import akka.http.scaladsl.model.headers.HttpCredentials

import scala.concurrent.Future

trait UserAuthenticator {
  def authenticate(credentials: HttpCredentials): Future[CurrentUser]
}
