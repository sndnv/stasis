package stasis.core.persistence.crates

import org.apache.pekko.NotUsed
import org.apache.pekko.stream.scaladsl.Source
import org.apache.pekko.util.ByteString
import stasis.core.packaging.Crate

import scala.concurrent.Future

trait CrateStoreView {
  def retrieve(crate: Crate.Id): Future[Option[Source[ByteString, NotUsed]]]
}
