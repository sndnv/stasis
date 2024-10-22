package stasis.server.persistence.datasets

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

import org.apache.pekko.Done

import stasis.layers.persistence.Store
import stasis.server.security.Resource
import stasis.shared.model.datasets.DatasetDefinition
import stasis.shared.model.devices.Device
import stasis.shared.security.Permission

trait DatasetDefinitionStore extends Store { store =>
  protected implicit def ec: ExecutionContext

  protected[datasets] def put(definition: DatasetDefinition): Future[Done]
  protected[datasets] def delete(definition: DatasetDefinition.Id): Future[Boolean]
  protected[datasets] def get(definition: DatasetDefinition.Id): Future[Option[DatasetDefinition]]
  protected[datasets] def list(): Future[Seq[DatasetDefinition]]

  final def view(): DatasetDefinitionStore.View.Privileged =
    new DatasetDefinitionStore.View.Privileged {
      def get(definition: DatasetDefinition.Id): Future[Option[DatasetDefinition]] =
        store.get(definition)

      def list(): Future[Seq[DatasetDefinition]] =
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

      override def list(ownDevices: Seq[Device.Id]): Future[Seq[DatasetDefinition]] =
        store.list().map(_.filter(d => ownDevices.contains(d.device)))
    }

  final def manage(): DatasetDefinitionStore.Manage.Privileged =
    new DatasetDefinitionStore.Manage.Privileged {
      override def put(definition: DatasetDefinition): Future[Done] =
        store.put(definition)

      override def delete(definition: DatasetDefinition.Id): Future[Boolean] =
        store.delete(definition)
    }

  final def manageSelf(): DatasetDefinitionStore.Manage.Self =
    new DatasetDefinitionStore.Manage.Self {
      override def put(ownDevices: Seq[Device.Id], definition: DatasetDefinition): Future[Done] =
        if (ownDevices.contains(definition.device)) {
          store.put(definition)
        } else {
          Future.failed(
            new IllegalArgumentException(
              s"Expected to put definition for own device but device [${definition.device.toString}] provided"
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
      def list(): Future[Seq[DatasetDefinition]]
      override def requiredPermission: Permission = Permission.View.Privileged
    }

    sealed trait Self extends Resource {
      def get(ownDevices: Seq[Device.Id], definition: DatasetDefinition.Id): Future[Option[DatasetDefinition]]
      def list(ownDevices: Seq[Device.Id]): Future[Seq[DatasetDefinition]]
      override def requiredPermission: Permission = Permission.View.Self
    }
  }

  object Manage {
    sealed trait Privileged extends Resource {
      def put(definition: DatasetDefinition): Future[Done]
      def delete(definition: DatasetDefinition.Id): Future[Boolean]
      override def requiredPermission: Permission = Permission.Manage.Privileged
    }
    sealed trait Self extends Resource {
      def put(ownDevices: Seq[Device.Id], definition: DatasetDefinition): Future[Done]
      def delete(ownDevices: Seq[Device.Id], definition: DatasetDefinition.Id): Future[Boolean]
      override def requiredPermission: Permission = Permission.Manage.Self
    }
  }
}
