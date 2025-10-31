package stasis.server.routing

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

import io.github.sndnv.layers.events.EventCollector
import org.apache.pekko.Done
import org.apache.pekko.NotUsed
import org.apache.pekko.stream.scaladsl.Source
import org.apache.pekko.util.ByteString

import stasis.core.packaging.Crate
import stasis.core.packaging.Manifest
import stasis.core.persistence.CrateStorageRequest
import stasis.core.persistence.CrateStorageReservation
import stasis.core.routing.Router
import stasis.server.events.Events.{Crates => Events}

class ServerRouter(underlying: Router)(implicit events: EventCollector) extends Router {
  override def push(manifest: Manifest, content: Source[ByteString, NotUsed]): Future[Done] =
    underlying
      .push(manifest = manifest, content = content)
      .map { result =>
        Events.CratePushed.recordWithAttributes(
          Events.Attributes.Crate.withValue(value = manifest.crate),
          Events.Attributes.Manifest.withValue(value = manifest)
        )
        result
      }(ExecutionContext.parasitic)

  override def pull(crate: Crate.Id): Future[Option[Source[ByteString, NotUsed]]] =
    underlying.pull(crate = crate)

  override def discard(crate: Crate.Id): Future[Done] =
    underlying
      .discard(crate = crate)
      .map { result =>
        Events.CrateDiscarded.recordWithAttributes(
          Events.Attributes.Crate.withValue(value = crate)
        )
        result
      }(ExecutionContext.parasitic)

  override def reserve(request: CrateStorageRequest): Future[Option[CrateStorageReservation]] =
    underlying.reserve(request = request)
}

object ServerRouter {
  def apply(underlying: Router)(implicit events: EventCollector): ServerRouter =
    new ServerRouter(underlying = underlying)
}
