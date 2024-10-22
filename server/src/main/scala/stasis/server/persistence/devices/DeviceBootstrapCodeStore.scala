package stasis.server.persistence.devices

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

import org.apache.pekko.Done

import stasis.layers.persistence.Store
import stasis.server.security.Resource
import stasis.server.security.exceptions.AuthorizationFailure
import stasis.shared.model.devices.Device
import stasis.shared.model.devices.DeviceBootstrapCode
import stasis.shared.security.Permission

trait DeviceBootstrapCodeStore extends Store { store =>
  protected implicit def ec: ExecutionContext

  protected[devices] def put(code: DeviceBootstrapCode): Future[Done]
  protected[devices] def delete(code: String): Future[Boolean]
  protected[devices] def consume(code: String): Future[Option[DeviceBootstrapCode]]
  protected[devices] def get(code: String): Future[Option[DeviceBootstrapCode]]
  protected[devices] def list(): Future[Seq[DeviceBootstrapCode]]
  protected[devices] def find(forDevice: Device.Id): Future[Option[DeviceBootstrapCode]]

  final def view(): DeviceBootstrapCodeStore.View.Privileged =
    new DeviceBootstrapCodeStore.View.Privileged {
      override def get(code: String): Future[Option[DeviceBootstrapCode]] =
        store.get(code)

      override def list(): Future[Seq[DeviceBootstrapCode]] =
        store.list()
    }

  final def viewSelf(): DeviceBootstrapCodeStore.View.Self =
    new DeviceBootstrapCodeStore.View.Self {
      override def get(ownDevices: Seq[Device.Id], code: String): Future[Option[DeviceBootstrapCode]] =
        store.get(code).flatMap {
          case Some(c) if ownDevices.contains(c.device) =>
            Future.successful(Some(c))

          case Some(c) =>
            Future.failed(
              new IllegalArgumentException(
                s"Expected to retrieve own device bootstrap code but code for device [${c.device.toString}] found"
              )
            )

          case None =>
            Future.successful(None)
        }

      override def list(ownDevices: Seq[Device.Id]): Future[Seq[DeviceBootstrapCode]] =
        store.list().map(_.filter(c => ownDevices.contains(c.device)))
    }

  final def manage(): DeviceBootstrapCodeStore.Manage.Privileged =
    new DeviceBootstrapCodeStore.Manage.Privileged {
      override def put(code: DeviceBootstrapCode): Future[Done] =
        store.put(code)

      override def delete(forDevice: Device.Id): Future[Boolean] =
        store.find(forDevice).flatMap {
          case Some(code) => store.delete(code.value)
          case None       => Future.successful(false)
        }

      override def consume(code: String): Future[Option[DeviceBootstrapCode]] =
        store.consume(code)
    }

  final def manageSelf(): DeviceBootstrapCodeStore.Manage.Self =
    new DeviceBootstrapCodeStore.Manage.Self {
      override def put(ownDevices: Seq[Device.Id], code: DeviceBootstrapCode): Future[Done] =
        if (ownDevices.contains(code.device)) {
          store.put(code)
        } else {
          Future.failed(
            AuthorizationFailure(
              s"Expected to put own device bootstrap code but code for device [${code.device.toString}] provided"
            )
          )
        }

      override def delete(ownDevices: Seq[Device.Id], forDevice: Device.Id): Future[Boolean] =
        if (ownDevices.contains(forDevice)) {
          store.find(forDevice).flatMap {
            case Some(code) => store.delete(code.value)
            case None       => Future.successful(false)
          }
        } else {
          Future.failed(
            AuthorizationFailure(
              s"Expected to delete own device bootstrap code but code for device [${forDevice.toString}] provided"
            )
          )
        }

    }
}

object DeviceBootstrapCodeStore {
  object View {
    sealed trait Privileged extends Resource {
      def get(code: String): Future[Option[DeviceBootstrapCode]]
      def list(): Future[Seq[DeviceBootstrapCode]]
      override def requiredPermission: Permission = Permission.View.Privileged
    }

    sealed trait Self extends Resource {
      def get(ownDevices: Seq[Device.Id], code: String): Future[Option[DeviceBootstrapCode]]
      def list(ownDevices: Seq[Device.Id]): Future[Seq[DeviceBootstrapCode]]
      override def requiredPermission: Permission = Permission.View.Self
    }
  }

  object Manage {
    sealed trait Privileged extends Resource {
      def put(code: DeviceBootstrapCode): Future[Done]
      def delete(forDevice: Device.Id): Future[Boolean]
      def consume(code: String): Future[Option[DeviceBootstrapCode]]
      override def requiredPermission: Permission = Permission.Manage.Privileged
    }

    sealed trait Self extends Resource {
      def put(ownDevices: Seq[Device.Id], code: DeviceBootstrapCode): Future[Done]
      def delete(ownDevices: Seq[Device.Id], forDevice: Device.Id): Future[Boolean]
      override def requiredPermission: Permission = Permission.Manage.Self
    }
  }
}
