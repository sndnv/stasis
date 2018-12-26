package stasis.persistence.backends

import akka.Done
import akka.util.ByteString

import scala.concurrent.Future

trait KeyValueBackend[K, V] {
  def init(): Future[Done]
  def drop(): Future[Done]
  def put(key: K, value: V): Future[Done]
  def delete(key: K): Future[Boolean]
  def get(key: K): Future[Option[V]]
  def map: Future[Map[K, V]]
}

object KeyValueBackend {
  trait Serdes[K, V] {
    implicit def serializeKey: K => String
    implicit def deserializeKey: String => K
    implicit def serializeValue: V => ByteString
    implicit def deserializeValue: ByteString => V
  }
}
