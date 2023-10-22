package stasis.client.security

import org.apache.pekko.Done
import org.apache.pekko.http.scaladsl.model.headers.HttpCredentials

import scala.concurrent.Future

trait FrontendAuthenticator {
  def authenticate(credentials: HttpCredentials): Future[Done]
}
