package stasis.core.persistence.manifests

import org.apache.pekko.Done
import stasis.core.packaging.{Crate, Manifest}
import stasis.core.persistence.Metrics
import stasis.core.persistence.backends.KeyValueBackend
import stasis.core.telemetry.TelemetryContext

import scala.concurrent.{ExecutionContext, Future}

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
    backend: KeyValueBackend[Crate.Id, Manifest]
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
