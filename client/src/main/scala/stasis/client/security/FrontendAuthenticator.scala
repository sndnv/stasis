package stasis.client.security

import scala.concurrent.Future

import akka.http.scaladsl.model.headers.HttpCredentials
import akka.Done

trait FrontendAuthenticator {
  def authenticate(credentials: HttpCredentials): Future[Done]
}
