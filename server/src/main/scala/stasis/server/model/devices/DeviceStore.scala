package stasis.server.model.devices

import akka.Done
import stasis.server.security.{Permission, Resource}
import stasis.server.model.users.User

import scala.concurrent.{ExecutionContext, Future}

trait DeviceStore { store =>
  protected implicit def ec: ExecutionContext

  protected def create(device: Device): Future[Done]
  protected def update(device: Device): Future[Done]
  protected def delete(device: Device.Id): Future[Boolean]
  protected def get(device: Device.Id): Future[Option[Device]]
  protected def list(): Future[Map[Device.Id, Device]]

  final def view(): DeviceStore.View.Privileged =
    new DeviceStore.View.Privileged {
      def get(device: Device.Id): Future[Option[Device]] =
        store.get(device)

      def list(): Future[Map[Device.Id, Device]] =
        store.list()
    }

  final def viewSelf(): DeviceStore.View.Self =
    new DeviceStore.View.Self {
      override def get(self: User.Id, device: Device.Id): Future[Option[Device]] =
        store.get(device).flatMap {
          case Some(d) if d.owner == self =>
            Future.successful(Some(d))

          case Some(d) =>
            Future.failed(
              new IllegalArgumentException(
                s"Expected to retrieve own [$self] device but device for user [${d.owner}] found"
              )
            )

          case None =>
            Future.successful(None)
        }

      override def list(self: User.Id): Future[Map[Device.Id, Device]] =
        store.list().map(_.filter(_._2.owner == self))
    }

  final def manage(): DeviceStore.Manage.Privileged =
    new DeviceStore.Manage.Privileged {
      override def create(device: Device): Future[Done] =
        store.create(device)

      override def update(device: Device): Future[Done] =
        store.update(device)

      override def delete(device: Device.Id): Future[Boolean] =
        store.delete(device)
    }

  final def manageSelf(): DeviceStore.Manage.Self =
    new DeviceStore.Manage.Self {
      override def create(self: User.Id, device: Device): Future[Done] =
        if (device.owner == self) {
          store.create(device)
        } else {
          Future.failed(
            new IllegalArgumentException(
              s"Expected to create own [$self] device but device for user [${device.owner}] provided"
            )
          )
        }

      override def update(self: User.Id, device: Device): Future[Done] =
        if (device.owner == self) {
          store.update(device)
        } else {
          Future.failed(
            new IllegalArgumentException(
              s"Expected to update own [$self] device but device for user [${device.owner}] provided"
            )
          )
        }

      override def delete(self: User.Id, device: Device.Id): Future[Boolean] =
        store.get(device).flatMap {
          case Some(d) =>
            if (d.owner == self) {
              store.delete(d.id)
            } else {
              Future.failed(
                new IllegalArgumentException(
                  s"Expected to delete own [$self] device but device for user [${d.owner}] provided"
                )
              )
            }

          case None =>
            Future.successful(false)
        }
    }
}

object DeviceStore {
  object View {
    sealed trait Privileged extends Resource {
      def get(device: Device.Id): Future[Option[Device]]
      def list(): Future[Map[Device.Id, Device]]
      override def requiredPermission: Permission = Permission.View.Privileged
    }

    sealed trait Self extends Resource {
      def get(self: User.Id, device: Device.Id): Future[Option[Device]]
      def list(self: User.Id): Future[Map[Device.Id, Device]]
      override def requiredPermission: Permission = Permission.View.Self
    }
  }

  object Manage {
    sealed trait Privileged extends Resource {
      def create(device: Device): Future[Done]
      def update(device: Device): Future[Done]
      def delete(device: Device.Id): Future[Boolean]
      override def requiredPermission: Permission = Permission.Manage.Privileged
    }

    sealed trait Self extends Resource {
      def create(self: User.Id, device: Device): Future[Done]
      def update(self: User.Id, device: Device): Future[Done]
      def delete(self: User.Id, device: Device.Id): Future[Boolean]
      override def requiredPermission: Permission = Permission.Manage.Self
    }
  }
}
