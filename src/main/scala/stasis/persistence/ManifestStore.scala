package stasis.persistence

import akka.Done
import stasis.packaging.{Crate, Manifest}

import scala.concurrent.Future

trait ManifestStore {
  def put(manifest: Manifest): Future[Done]
  def get(crate: Crate.Id): Future[Option[Manifest]]
}
