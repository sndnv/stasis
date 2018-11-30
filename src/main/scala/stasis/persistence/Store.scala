package stasis.persistence

import akka.{Done, NotUsed}
import akka.stream.scaladsl.Source
import akka.util.ByteString
import stasis.packaging.{Crate, Manifest}

import scala.concurrent.Future

trait Store {
  def persist(manifest: Manifest, content: Source[ByteString, NotUsed]): Future[Done]
  def retrieve(crate: Crate.Id): Future[Option[Source[ByteString, NotUsed]]]
}
