package stasis.server.model.devices

import akka.Done
import stasis.core.persistence.backends.KeyValueBackend
import stasis.server.security.{CurrentUser, Resource}
import stasis.shared.model.devices.Device
import stasis.shared.security.Permission

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
      override def get(self: CurrentUser, device: Device.Id): Future[Option[Device]] =
        store.get(device).flatMap {
          case Some(d) if d.owner == self.id =>
            Future.successful(Some(d))

          case Some(d) =>
            Future.failed(
              new IllegalArgumentException(
                s"Expected to retrieve own [${self.id.toString}] device but device for user [${d.owner.toString}] found"
              )
            )

          case None =>
            Future.successful(None)
        }

      override def list(self: CurrentUser): Future[Map[Device.Id, Device]] =
        store.list().map(_.filter(_._2.owner == self.id))
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
      override def create(self: CurrentUser, device: Device): Future[Done] =
        if (device.owner == self.id) {
          store.create(device)
        } else {
          Future.failed(
            new IllegalArgumentException(
              s"Expected to create own [${self.id.toString}] device but device for user [${device.owner.toString}] provided"
            )
          )
        }

      override def update(self: CurrentUser, device: Device): Future[Done] =
        if (device.owner == self.id) {
          store.update(device)
        } else {
          Future.failed(
            new IllegalArgumentException(
              s"Expected to update own [${self.id.toString}] device but device for user [${device.owner.toString}] provided"
            )
          )
        }

      override def delete(self: CurrentUser, device: Device.Id): Future[Boolean] =
        store.get(device).flatMap {
          case Some(d) =>
            if (d.owner == self.id) {
              store.delete(d.id)
            } else {
              Future.failed(
                new IllegalArgumentException(
                  s"Expected to delete own [${self.id.toString}] device but device for user [${d.owner.toString}] provided"
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
      def get(self: CurrentUser, device: Device.Id): Future[Option[Device]]
      def list(self: CurrentUser): Future[Map[Device.Id, Device]]
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
      def create(self: CurrentUser, device: Device): Future[Done]
      def update(self: CurrentUser, device: Device): Future[Done]
      def delete(self: CurrentUser, device: Device.Id): Future[Boolean]
      override def requiredPermission: Permission = Permission.Manage.Self
    }
  }

  def apply(
    backend: KeyValueBackend[Device.Id, Device]
  )(implicit ctx: ExecutionContext): DeviceStore =
    new DeviceStore {
      override implicit protected def ec: ExecutionContext = ctx
      override protected def create(device: Device): Future[Done] = backend.put(device.id, device)
      override protected def update(device: Device): Future[Done] = backend.put(device.id, device)
      override protected def delete(device: Device.Id): Future[Boolean] = backend.delete(device)
      override protected def get(device: Device.Id): Future[Option[Device]] = backend.get(device)
      override protected def list(): Future[Map[Device.Id, Device]] = backend.entries
    }
}
