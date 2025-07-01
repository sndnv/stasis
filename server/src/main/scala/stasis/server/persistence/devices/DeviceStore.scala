package stasis.server.persistence.devices

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

import org.apache.pekko.Done

import io.github.sndnv.layers.persistence.Store
import stasis.server.security.CurrentUser
import stasis.server.security.Resource
import stasis.shared.model.devices.Device
import stasis.shared.security.Permission

trait DeviceStore extends Store { store =>
  protected implicit def ec: ExecutionContext

  protected[devices] def put(device: Device): Future[Done]
  protected[devices] def delete(device: Device.Id): Future[Boolean]
  protected[devices] def get(device: Device.Id): Future[Option[Device]]
  protected[devices] def list(): Future[Seq[Device]]

  final def view(): DeviceStore.View.Privileged =
    new DeviceStore.View.Privileged {
      def get(device: Device.Id): Future[Option[Device]] =
        store.get(device)

      def list(): Future[Seq[Device]] =
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

      override def list(self: CurrentUser): Future[Seq[Device]] =
        store.list().map(_.filter(_.owner == self.id))
    }

  final def manage(): DeviceStore.Manage.Privileged =
    new DeviceStore.Manage.Privileged {
      override def put(device: Device): Future[Done] =
        store.put(device)

      override def delete(device: Device.Id): Future[Boolean] =
        store.delete(device)
    }

  final def manageSelf(): DeviceStore.Manage.Self =
    new DeviceStore.Manage.Self {
      override def put(self: CurrentUser, device: Device): Future[Done] =
        if (device.owner == self.id) {
          store.put(device)
        } else {
          Future.failed(
            new IllegalArgumentException(
              s"Expected to put own [${self.id.toString}] device but device for user [${device.owner.toString}] provided"
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
      def list(): Future[Seq[Device]]
      override def requiredPermission: Permission = Permission.View.Privileged
    }

    sealed trait Self extends Resource {
      def get(self: CurrentUser, device: Device.Id): Future[Option[Device]]
      def list(self: CurrentUser): Future[Seq[Device]]
      override def requiredPermission: Permission = Permission.View.Self
    }
  }

  object Manage {
    sealed trait Privileged extends Resource {
      def put(device: Device): Future[Done]
      def delete(device: Device.Id): Future[Boolean]
      override def requiredPermission: Permission = Permission.Manage.Privileged
    }

    sealed trait Self extends Resource {
      def put(self: CurrentUser, device: Device): Future[Done]
      def delete(self: CurrentUser, device: Device.Id): Future[Boolean]
      override def requiredPermission: Permission = Permission.Manage.Self
    }
  }
}
