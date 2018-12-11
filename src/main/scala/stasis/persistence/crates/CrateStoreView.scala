package stasis.persistence.crates

import akka.NotUsed
import akka.stream.scaladsl.Source
import akka.util.ByteString
import stasis.packaging.Crate

import scala.concurrent.Future

trait CrateStoreView {
  def retrieve(crate: Crate.Id): Future[Option[Source[ByteString, NotUsed]]]
}
