package stasis.persistence.manifests

import stasis.packaging.{Crate, Manifest}

import scala.concurrent.Future

trait ManifestStoreView {
  def get(crate: Crate.Id): Future[Option[Manifest]]
}
