package stasis.routing
import akka.stream.scaladsl.Source
import akka.util.ByteString
import akka.{Done, NotUsed}
import stasis.packaging
import stasis.packaging.Crate.Id
import stasis.persistence.CrateStore

import scala.concurrent.Future

class LocalRouter(store: CrateStore) extends Router {
  override def push(
    manifest: packaging.Manifest,
    content: Source[ByteString, NotUsed]
  ): Future[Done] = store.persist(manifest, content)

  override def pull(
    crate: Id
  ): Future[Option[Source[ByteString, NotUsed]]] = store.retrieve(crate)
}
