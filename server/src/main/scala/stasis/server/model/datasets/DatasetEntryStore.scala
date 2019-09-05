package stasis.server.model.datasets

import scala.concurrent.{ExecutionContext, Future}
import akka.Done
import stasis.core.persistence.backends.KeyValueBackend
import stasis.server.security.Resource
import stasis.shared.model.datasets.{DatasetDefinition, DatasetEntry}
import stasis.shared.model.devices.Device
import stasis.shared.security.Permission

trait DatasetEntryStore { store =>
  protected implicit def ec: ExecutionContext

  protected def create(entry: DatasetEntry): Future[Done]
  protected def delete(entry: DatasetEntry.Id): Future[Boolean]
  protected def get(entry: DatasetEntry.Id): Future[Option[DatasetEntry]]
  protected def list(definition: DatasetDefinition.Id): Future[Map[DatasetEntry.Id, DatasetEntry]]

  final def view(): DatasetEntryStore.View.Privileged =
    new DatasetEntryStore.View.Privileged {
      override def get(entry: DatasetEntry.Id): Future[Option[DatasetEntry]] =
        store.get(entry)

      override def list(definition: DatasetDefinition.Id): Future[Map[DatasetEntry.Id, DatasetEntry]] =
        store.list(definition)
    }

  final def viewSelf(): DatasetEntryStore.View.Self =
    new DatasetEntryStore.View.Self {
      override def get(
        ownDevices: Seq[Device.Id],
        entry: DatasetEntry.Id
      ): Future[Option[DatasetEntry]] =
        store.get(entry).flatMap {
          case Some(e) if ownDevices.contains(e.device) =>
            Future.successful(Some(e))

          case Some(e) =>
            Future.failed(
              new IllegalArgumentException(
                s"Expected to retrieve entry for own device but device [${e.device}] found"
              )
            )

          case None =>
            Future.successful(None)
        }

      override def list(
        ownDevices: Seq[Device.Id],
        definition: DatasetDefinition.Id
      ): Future[Map[DatasetEntry.Id, DatasetEntry]] =
        store.list(definition).map(_.filter(e => ownDevices.contains(e._2.device)))
    }

  final def manage(): DatasetEntryStore.Manage.Privileged =
    new DatasetEntryStore.Manage.Privileged {
      override def create(entry: DatasetEntry): Future[Done] =
        store.create(entry)

      override def delete(entry: DatasetEntry.Id): Future[Boolean] =
        store.delete(entry)
    }

  final def manageSelf(): DatasetEntryStore.Manage.Self =
    new DatasetEntryStore.Manage.Self {
      override def create(ownDevices: Seq[Device.Id], entry: DatasetEntry): Future[Done] =
        if (ownDevices.contains(entry.device)) {
          store.create(entry)
        } else {
          Future.failed(
            new IllegalArgumentException(
              s"Expected to create entry for own device but device [${entry.device}] provided"
            )
          )
        }

      override def delete(ownDevices: Seq[Device.Id], entry: DatasetEntry.Id): Future[Boolean] =
        store.get(entry).flatMap {
          case Some(e) =>
            if (ownDevices.contains(e.device)) {
              store.delete(e.id)
            } else {
              Future.failed(
                new IllegalArgumentException(
                  s"Expected to delete entry for own device but device [${e.device}] provided"
                )
              )
            }

          case None =>
            Future.successful(false)
        }
    }
}

object DatasetEntryStore {
  object View {
    sealed trait Privileged extends Resource {
      def get(entry: DatasetEntry.Id): Future[Option[DatasetEntry]]
      def list(definition: DatasetDefinition.Id): Future[Map[DatasetEntry.Id, DatasetEntry]]
      override def requiredPermission: Permission = Permission.View.Privileged
    }

    sealed trait Self extends Resource {
      def get(ownDevices: Seq[Device.Id], entry: DatasetEntry.Id): Future[Option[DatasetEntry]]
      def list(ownDevices: Seq[Device.Id], definition: DatasetDefinition.Id): Future[Map[DatasetEntry.Id, DatasetEntry]]
      override def requiredPermission: Permission = Permission.View.Self
    }
  }

  object Manage {
    sealed trait Privileged extends Resource {
      def create(entry: DatasetEntry): Future[Done]
      def delete(entry: DatasetEntry.Id): Future[Boolean]
      override def requiredPermission: Permission = Permission.Manage.Privileged
    }

    sealed trait Self extends Resource {
      def create(ownDevices: Seq[Device.Id], entry: DatasetEntry): Future[Done]
      def delete(ownDevices: Seq[Device.Id], entry: DatasetEntry.Id): Future[Boolean]
      override def requiredPermission: Permission = Permission.Manage.Self
    }
  }

  def apply(
    backend: KeyValueBackend[DatasetEntry.Id, DatasetEntry]
  )(implicit ctx: ExecutionContext): DatasetEntryStore =
    new DatasetEntryStore {
      override implicit protected def ec: ExecutionContext = ctx

      override protected def create(entry: DatasetEntry): Future[Done] =
        backend.put(entry.id, entry)

      override protected def delete(entry: DatasetEntry.Id): Future[Boolean] =
        backend.delete(entry)

      override protected def get(entry: DatasetEntry.Id): Future[Option[DatasetEntry]] =
        backend.get(entry)

      override protected def list(definition: DatasetEntry.Id): Future[Map[DatasetEntry.Id, DatasetEntry]] =
        backend.entries.map(_.filter(_._2.definition == definition))(ctx)
    }
}
