package stasis.client.security

import scala.concurrent.Future

import org.apache.pekko.Done
import org.apache.pekko.http.scaladsl.model.headers.HttpCredentials

trait FrontendAuthenticator {
  def authenticate(credentials: HttpCredentials): Future[Done]
}
