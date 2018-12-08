package stasis.persistence

import akka.stream.scaladsl.Source
import akka.util.ByteString
import akka.{Done, NotUsed}
import stasis.packaging.{Crate, Manifest}

import scala.concurrent.Future

trait CrateStore {
  def persist(manifest: Manifest, content: Source[ByteString, NotUsed]): Future[Done]
  def retrieve(crate: Crate.Id): Future[Option[Source[ByteString, NotUsed]]]
}
