package stasis.routing
import akka.{Done, NotUsed}
import akka.stream.scaladsl.Source
import akka.util.ByteString
import stasis.packaging
import stasis.packaging.Crate.Id
import stasis.persistence.Store

import scala.concurrent.Future

class LocalRouter(store: Store) extends Router {
  override def push(
    manifest: packaging.Manifest,
    content: Source[ByteString, NotUsed]
  ): Future[Done] = store.persist(manifest, content)

  override def pull(
    crate: Id
  ): Future[Option[Source[ByteString, NotUsed]]] = store.retrieve(crate)
}
