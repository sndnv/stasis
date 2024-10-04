package stasis.server.model.manifests

import scala.concurrent.Future

import stasis.core.packaging.Crate
import stasis.core.packaging.Manifest
import stasis.core.persistence.manifests.ManifestStore
import stasis.server.security.Resource
import stasis.shared.security.Permission

trait ServerManifestStore { store =>
  protected def get(crate: Crate.Id): Future[Option[Manifest]]
  protected def delete(crate: Crate.Id): Future[Boolean]

  final def view(): ServerManifestStore.View.Service =
    new ServerManifestStore.View.Service {
      override def get(crate: Crate.Id): Future[Option[Manifest]] = store.get(crate)
    }

  final def manage(): ServerManifestStore.Manage.Service =
    new ServerManifestStore.Manage.Service {
      override def delete(crate: Crate.Id): Future[Boolean] = store.delete(crate)
    }
}

object ServerManifestStore {
  object Manage {
    sealed trait Service extends Resource {
      def delete(crate: Crate.Id): Future[Boolean]
      override def requiredPermission: Permission = Permission.Manage.Service
    }
  }

  object View {
    sealed trait Service extends Resource {
      def get(crate: Crate.Id): Future[Option[Manifest]]
      override def requiredPermission: Permission = Permission.View.Service
    }
  }

  def apply(store: ManifestStore): ServerManifestStore =
    new ServerManifestStore {
      override protected def get(crate: Crate.Id): Future[Option[Manifest]] = store.get(crate)
      override protected def delete(crate: Crate.Id): Future[Boolean] = store.delete(crate)
    }
}
