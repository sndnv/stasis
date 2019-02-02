package stasis.core.persistence.manifests

import stasis.core.packaging.{Crate, Manifest}

import scala.concurrent.Future

trait ManifestStoreView {
  def get(crate: Crate.Id): Future[Option[Manifest]]
}
