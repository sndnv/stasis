package stasis.security.jwt

import java.security.Key

import scala.concurrent.Future

trait JwtKeyProvider {
  def key(id: Option[String]): Future[Key]
  def issuer: String
  def allowedAlgorithms: Seq[String]
}
