package stasis.server.persistence.analytics

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

import org.apache.pekko.Done

import stasis.layers.persistence.Store
import stasis.server.security.Resource
import stasis.shared.model.analytics.StoredAnalyticsEntry
import stasis.shared.security.Permission

trait AnalyticsEntryStore extends Store { store =>
  protected implicit def ec: ExecutionContext

  protected[analytics] def put(entry: StoredAnalyticsEntry): Future[Done]
  protected[analytics] def delete(entry: StoredAnalyticsEntry.Id): Future[Boolean]
  protected[analytics] def get(entry: StoredAnalyticsEntry.Id): Future[Option[StoredAnalyticsEntry]]
  protected[analytics] def list(): Future[Seq[StoredAnalyticsEntry]]

  final def view(): AnalyticsEntryStore.View.Service = new AnalyticsEntryStore.View.Service {
    override def get(entry: StoredAnalyticsEntry.Id): Future[Option[StoredAnalyticsEntry]] =
      store.get(entry)

    override def list(): Future[Seq[StoredAnalyticsEntry]] =
      store.list()
  }

  final def manage(): AnalyticsEntryStore.Manage.Service = new AnalyticsEntryStore.Manage.Service {
    override def delete(entry: StoredAnalyticsEntry.Id): Future[Boolean] =
      store.delete(entry)
  }

  final def manageSelf(): AnalyticsEntryStore.Manage.Self = new AnalyticsEntryStore.Manage.Self {
    override def create(entry: StoredAnalyticsEntry): Future[Done] =
      store.put(entry)
  }
}

object AnalyticsEntryStore {
  object View {
    sealed trait Service extends Resource {
      def get(entry: StoredAnalyticsEntry.Id): Future[Option[StoredAnalyticsEntry]]
      def list(): Future[Seq[StoredAnalyticsEntry]]
      override def requiredPermission: Permission = Permission.View.Service
    }
  }

  object Manage {
    sealed trait Service extends Resource {
      def delete(entry: StoredAnalyticsEntry.Id): Future[Boolean]
      override def requiredPermission: Permission = Permission.Manage.Service
    }

    sealed trait Self extends Resource {
      def create(entry: StoredAnalyticsEntry): Future[Done]
      override def requiredPermission: Permission = Permission.Manage.Self
    }
  }
}
