package stasis.layers.persistence

import scala.concurrent.Future

import org.apache.pekko.Done

trait KeyValueStore[K, V] extends Store {
  def put(key: K, value: V): Future[Done]
  def get(key: K): Future[Option[V]]
  def delete(key: K): Future[Boolean]
  def contains(key: K): Future[Boolean]
  def entries: Future[Map[K, V]]
}
