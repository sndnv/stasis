package stasis.client.security

import org.apache.pekko.http.scaladsl.model.headers.HttpCredentials

import scala.concurrent.Future

trait CredentialsProvider {
  def core: Future[HttpCredentials]
  def api: Future[HttpCredentials]
}
