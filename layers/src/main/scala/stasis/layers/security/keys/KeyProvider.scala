package stasis.layers.security.keys

import java.security.Key

import scala.concurrent.Future

trait KeyProvider {
  def key(id: Option[String]): Future[Key]
  def issuer: String
  def allowedAlgorithms: Seq[String]
}
