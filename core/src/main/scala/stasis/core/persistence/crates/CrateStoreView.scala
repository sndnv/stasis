package stasis.core.persistence.crates

import scala.concurrent.Future

import org.apache.pekko.NotUsed
import org.apache.pekko.stream.scaladsl.Source
import org.apache.pekko.util.ByteString

import stasis.core.packaging.Crate

trait CrateStoreView {
  def retrieve(crate: Crate.Id): Future[Option[Source[ByteString, NotUsed]]]
}
