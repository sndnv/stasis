package stasis.persistence.backends

import akka.stream.scaladsl.{Sink, Source}
import akka.util.ByteString
import akka.{Done, NotUsed}

import scala.concurrent.Future

trait StreamingBackend[K] {
  def sink(key: K): Future[Sink[ByteString, Future[Done]]]
  def source(key: K): Future[Option[Source[ByteString, NotUsed]]]
}
