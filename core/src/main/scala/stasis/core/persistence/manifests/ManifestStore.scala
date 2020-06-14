package stasis.core.persistence.manifests

import akka.Done
import stasis.core.packaging.{Crate, Manifest}
import stasis.core.persistence.backends.KeyValueBackend

import scala.concurrent.Future

trait ManifestStore { store =>
  def put(manifest: Manifest): Future[Done]
  def delete(crate: Crate.Id): Future[Boolean]
  def get(crate: Crate.Id): Future[Option[Manifest]]

  def view: ManifestStoreView = new ManifestStoreView {
    override def get(crate: Crate.Id): Future[Option[Manifest]] = store.get(crate)
  }
}

object ManifestStore {
  def apply(backend: KeyValueBackend[Crate.Id, Manifest]): ManifestStore = new ManifestStore {
    override def put(manifest: Manifest): Future[Done] = backend.put(manifest.crate, manifest)
    override def delete(crate: Crate.Id): Future[Boolean] = backend.delete(crate)
    override def get(crate: Crate.Id): Future[Option[Manifest]] = backend.get(crate)
  }
}
