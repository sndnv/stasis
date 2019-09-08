package stasis.core.persistence.backends

import java.util.UUID

import akka.stream.scaladsl.{Sink, Source}
import akka.util.ByteString
import akka.{Done, NotUsed}

import scala.concurrent.Future

trait StreamingBackend {
  def init(): Future[Done]
  def drop(): Future[Done]
  def sink(key: UUID): Future[Sink[ByteString, Future[Done]]]
  def source(key: UUID): Future[Option[Source[ByteString, NotUsed]]]
  def delete(key: UUID): Future[Boolean]
  def contains(key: UUID): Future[Boolean]
  def canStore(bytes: Long): Future[Boolean]
}
