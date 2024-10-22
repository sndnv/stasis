package stasis.core.persistence.manifests

import scala.concurrent.Future

import org.apache.pekko.Done

import stasis.core.packaging.Crate
import stasis.core.packaging.Manifest
import stasis.layers.persistence.Store

trait ManifestStore extends Store { store =>
  def put(manifest: Manifest): Future[Done]
  def delete(crate: Crate.Id): Future[Boolean]
  def get(crate: Crate.Id): Future[Option[Manifest]]
  def list(): Future[Seq[Manifest]]

  def view: ManifestStore.View =
    new ManifestStore.View {
      override def get(crate: Crate.Id): Future[Option[Manifest]] = store.get(crate)
    }
}

object ManifestStore {
  trait View {
    def get(crate: Crate.Id): Future[Option[Manifest]]
  }
}
