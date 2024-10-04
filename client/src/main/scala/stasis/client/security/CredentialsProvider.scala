package stasis.client.security

import scala.concurrent.Future

import org.apache.pekko.http.scaladsl.model.headers.HttpCredentials

trait CredentialsProvider {
  def core: Future[HttpCredentials]
  def api: Future[HttpCredentials]
}
