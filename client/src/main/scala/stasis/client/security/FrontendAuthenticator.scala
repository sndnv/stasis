package stasis.client.security

import akka.Done
import akka.http.scaladsl.model.headers.HttpCredentials

import scala.concurrent.Future

trait FrontendAuthenticator {
  def authenticate(credentials: HttpCredentials): Future[Done]
}
