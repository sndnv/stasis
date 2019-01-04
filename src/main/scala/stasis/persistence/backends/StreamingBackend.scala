package stasis.persistence.backends

import akka.stream.scaladsl.{Sink, Source}
import akka.util.ByteString
import akka.{Done, NotUsed}

import scala.concurrent.Future

trait StreamingBackend[K] {
  def init(): Future[Done]
  def drop(): Future[Done]
  def sink(key: K): Future[Sink[ByteString, Future[Done]]]
  def source(key: K): Future[Option[Source[ByteString, NotUsed]]]
  def delete(key: K): Future[Boolean]
  def contains(key: K): Future[Boolean]
  def canStore(bytes: Long): Future[Boolean]
}
