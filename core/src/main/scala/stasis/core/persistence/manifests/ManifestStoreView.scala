package stasis.core.persistence.manifests

import scala.concurrent.Future

import stasis.core.packaging.Crate
import stasis.core.packaging.Manifest

trait ManifestStoreView {
  def get(crate: Crate.Id): Future[Option[Manifest]]
}
