package stasis.client.security

import akka.http.scaladsl.model.headers.HttpCredentials

import scala.concurrent.Future

trait CredentialsProvider {
  def core: Future[HttpCredentials]
  def api: Future[HttpCredentials]
}
