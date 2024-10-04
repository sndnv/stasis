package stasis.core.persistence.manifests

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

import org.apache.pekko.Done

import stasis.core.packaging.Crate
import stasis.core.packaging.Manifest
import stasis.core.persistence.Metrics
import stasis.layers.persistence.KeyValueStore
import stasis.layers.telemetry.TelemetryContext

trait ManifestStore { store =>
  def put(manifest: Manifest): Future[Done]
  def delete(crate: Crate.Id): Future[Boolean]
  def get(crate: Crate.Id): Future[Option[Manifest]]

  def view: ManifestStoreView =
    new ManifestStoreView {
      override def get(crate: Crate.Id): Future[Option[Manifest]] = store.get(crate)
    }
}

object ManifestStore {
  def apply(
    backend: KeyValueStore[Crate.Id, Manifest]
  )(implicit ec: ExecutionContext, telemetry: TelemetryContext): ManifestStore =
    new ManifestStore {
      private val metrics = telemetry.metrics[Metrics.ManifestStore]

      override def put(manifest: Manifest): Future[Done] =
        backend
          .put(manifest.crate, manifest)
          .map { result =>
            metrics.recordManifest(manifest)
            result
          }

      override def delete(crate: Crate.Id): Future[Boolean] =
        backend.delete(crate)

      override def get(crate: Crate.Id): Future[Option[Manifest]] =
        backend.get(crate)
    }
}
