package stasis.server.model.staging

import stasis.core.packaging.Crate
import stasis.core.persistence.staging.StagingStore
import stasis.core.persistence.staging.StagingStore.PendingDestaging
import stasis.server.security.Resource
import stasis.shared.security.Permission

import scala.concurrent.Future

trait ServerStagingStore { store =>
  protected def list(): Future[Map[Crate.Id, PendingDestaging]]
  protected def drop(crate: Crate.Id): Future[Boolean]

  def view(): ServerStagingStore.View.Service =
    new ServerStagingStore.View.Service {
      override def list(): Future[Map[Crate.Id, PendingDestaging]] = store.list()
    }

  def manage(): ServerStagingStore.Manage.Service =
    new ServerStagingStore.Manage.Service {
      override def drop(crate: Crate.Id): Future[Boolean] = store.drop(crate)
    }
}

object ServerStagingStore {
  object View {
    sealed trait Service extends Resource {
      def list(): Future[Map[Crate.Id, PendingDestaging]]
      override def requiredPermission: Permission = Permission.View.Service
    }
  }

  object Manage {
    sealed trait Service extends Resource {
      def drop(crate: Crate.Id): Future[Boolean]
      override def requiredPermission: Permission = Permission.Manage.Service
    }
  }

  def apply(store: StagingStore): ServerStagingStore =
    new ServerStagingStore {
      override protected def list(): Future[Map[Crate.Id, PendingDestaging]] = store.pending
      override protected def drop(crate: Crate.Id): Future[Boolean] = store.drop(crate)
    }
}
