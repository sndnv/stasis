package stasis.persistence.manifests

import akka.Done
import stasis.packaging.Crate.Id
import stasis.packaging.{Crate, Manifest}

import scala.concurrent.Future

trait ManifestStore { store =>
  def put(manifest: Manifest): Future[Done]
  def delete(crate: Crate.Id): Future[Boolean]
  def get(crate: Crate.Id): Future[Option[Manifest]]

  def view: ManifestStoreView = new ManifestStoreView {
    override def get(crate: Id): Future[Option[Manifest]] = store.get(crate)
  }
}
