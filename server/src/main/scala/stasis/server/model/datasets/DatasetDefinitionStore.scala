package stasis.server.model.datasets

import org.apache.pekko.Done
import stasis.core.persistence.backends.KeyValueBackend
import stasis.server.security.Resource
import stasis.shared.model.datasets.DatasetDefinition
import stasis.shared.model.devices.Device
import stasis.shared.security.Permission

import scala.concurrent.{ExecutionContext, Future}

trait DatasetDefinitionStore { store =>
  protected implicit def ec: ExecutionContext

  protected def create(definition: DatasetDefinition): Future[Done]
  protected def update(definition: DatasetDefinition): Future[Done]
  protected def delete(definition: DatasetDefinition.Id): Future[Boolean]
  protected def get(definition: DatasetDefinition.Id): Future[Option[DatasetDefinition]]
  protected def list(): Future[Map[DatasetDefinition.Id, DatasetDefinition]]

  final def view(): DatasetDefinitionStore.View.Privileged =
    new DatasetDefinitionStore.View.Privileged {
      def get(definition: DatasetDefinition.Id): Future[Option[DatasetDefinition]] =
        store.get(definition)

      def list(): Future[Map[DatasetDefinition.Id, DatasetDefinition]] =
        store.list()
    }

  final def viewSelf(): DatasetDefinitionStore.View.Self =
    new DatasetDefinitionStore.View.Self {
      override def get(
        ownDevices: Seq[Device.Id],
        definition: DatasetDefinition.Id
      ): Future[Option[DatasetDefinition]] =
        store.get(definition).flatMap {
          case Some(d) if ownDevices.contains(d.device) =>
            Future.successful(Some(d))

          case Some(d) =>
            Future.failed(
              new IllegalArgumentException(
                s"Expected to retrieve definition for own device but device [${d.device.toString}] found"
              )
            )

          case None =>
            Future.successful(None)
        }

      override def list(ownDevices: Seq[Device.Id]): Future[Map[DatasetDefinition.Id, DatasetDefinition]] =
        store.list().map(_.filter(d => ownDevices.contains(d._2.device)))
    }

  final def manage(): DatasetDefinitionStore.Manage.Privileged =
    new DatasetDefinitionStore.Manage.Privileged {
      override def create(definition: DatasetDefinition): Future[Done] =
        store.create(definition)

      override def update(definition: DatasetDefinition): Future[Done] =
        store.update(definition)

      override def delete(definition: DatasetDefinition.Id): Future[Boolean] =
        store.delete(definition)
    }

  final def manageSelf(): DatasetDefinitionStore.Manage.Self =
    new DatasetDefinitionStore.Manage.Self {
      override def create(ownDevices: Seq[Device.Id], definition: DatasetDefinition): Future[Done] =
        if (ownDevices.contains(definition.device)) {
          store.create(definition)
        } else {
          Future.failed(
            new IllegalArgumentException(
              s"Expected to create definition for own device but device [${definition.device.toString}] provided"
            )
          )
        }

      override def update(ownDevices: Seq[Device.Id], definition: DatasetDefinition): Future[Done] =
        if (ownDevices.contains(definition.device)) {
          store.update(definition)
        } else {
          Future.failed(
            new IllegalArgumentException(
              s"Expected to update definition for own device but device [${definition.device.toString}] provided"
            )
          )
        }

      override def delete(ownDevices: Seq[Device.Id], definition: DatasetDefinition.Id): Future[Boolean] =
        store.get(definition).flatMap {
          case Some(d) =>
            if (ownDevices.contains(d.device)) {
              store.delete(d.id)
            } else {
              Future.failed(
                new IllegalArgumentException(
                  s"Expected to delete definition for own device but device [${d.device.toString}] provided"
                )
              )
            }

          case None =>
            Future.successful(false)
        }
    }
}

object DatasetDefinitionStore {
  object View {
    sealed trait Privileged extends Resource {
      def get(definition: DatasetDefinition.Id): Future[Option[DatasetDefinition]]
      def list(): Future[Map[DatasetDefinition.Id, DatasetDefinition]]
      override def requiredPermission: Permission = Permission.View.Privileged
    }

    sealed trait Self extends Resource {
      def get(ownDevices: Seq[Device.Id], definition: DatasetDefinition.Id): Future[Option[DatasetDefinition]]
      def list(ownDevices: Seq[Device.Id]): Future[Map[DatasetDefinition.Id, DatasetDefinition]]
      override def requiredPermission: Permission = Permission.View.Self
    }
  }

  object Manage {
    sealed trait Privileged extends Resource {
      def create(definition: DatasetDefinition): Future[Done]
      def update(definition: DatasetDefinition): Future[Done]
      def delete(definition: DatasetDefinition.Id): Future[Boolean]
      override def requiredPermission: Permission = Permission.Manage.Privileged
    }
    sealed trait Self extends Resource {
      def create(ownDevices: Seq[Device.Id], definition: DatasetDefinition): Future[Done]
      def update(ownDevices: Seq[Device.Id], definition: DatasetDefinition): Future[Done]
      def delete(ownDevices: Seq[Device.Id], definition: DatasetDefinition.Id): Future[Boolean]
      override def requiredPermission: Permission = Permission.Manage.Self
    }
  }

  def apply(
    backend: KeyValueBackend[DatasetDefinition.Id, DatasetDefinition]
  )(implicit ctx: ExecutionContext): DatasetDefinitionStore =
    new DatasetDefinitionStore {
      override implicit protected def ec: ExecutionContext = ctx

      override protected def create(definition: DatasetDefinition): Future[Done] =
        backend.put(definition.id, definition)

      override protected def update(definition: DatasetDefinition): Future[Done] =
        backend.put(definition.id, definition)

      override protected def delete(definition: DatasetDefinition.Id): Future[Boolean] =
        backend.delete(definition)

      override protected def get(definition: DatasetDefinition.Id): Future[Option[DatasetDefinition]] =
        backend.get(definition)

      override protected def list(): Future[Map[DatasetDefinition.Id, DatasetDefinition]] =
        backend.entries
    }

}
