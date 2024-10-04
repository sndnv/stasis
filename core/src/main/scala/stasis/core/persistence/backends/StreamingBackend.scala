package stasis.core.persistence.backends

import java.util.UUID

import scala.concurrent.Future

import org.apache.pekko.Done
import org.apache.pekko.NotUsed
import org.apache.pekko.stream.scaladsl.Sink
import org.apache.pekko.stream.scaladsl.Source
import org.apache.pekko.util.ByteString

trait StreamingBackend {
  def info: String
  def init(): Future[Done]
  def drop(): Future[Done]
  def available(): Future[Boolean]
  def sink(key: UUID): Future[Sink[ByteString, Future[Done]]]
  def source(key: UUID): Future[Option[Source[ByteString, NotUsed]]]
  def delete(key: UUID): Future[Boolean]
  def contains(key: UUID): Future[Boolean]
  def canStore(bytes: Long): Future[Boolean]
}
