package stasis.server.model.devices

import org.apache.pekko.Done
import org.apache.pekko.actor.typed.{ActorSystem, SpawnProtocol}
import org.apache.pekko.util.ByteString
import stasis.core.persistence.backends.KeyValueBackend
import stasis.server.security.Resource
import stasis.server.security.exceptions.AuthorizationFailure
import stasis.shared.model.devices.{Device, DeviceKey}
import stasis.shared.security.Permission

import scala.concurrent.{ExecutionContext, Future}

trait DeviceKeyStore { store =>
  protected implicit def ec: ExecutionContext

  protected def put(key: DeviceKey): Future[Done]
  protected def delete(forDevice: Device.Id): Future[Boolean]
  protected def exists(forDevice: Device.Id): Future[Boolean]
  protected def get(forDevice: Device.Id): Future[Option[DeviceKey]]
  protected def list(): Future[Seq[DeviceKey]]

  final def view(): DeviceKeyStore.View.Privileged =
    new DeviceKeyStore.View.Privileged {
      override def get(forDevice: Device.Id): Future[Option[DeviceKey]] =
        store.get(forDevice).map(_.map(_.copy(value = ByteString.empty)))

      override def list(): Future[Seq[DeviceKey]] =
        store.list()
    }

  final def viewSelf(): DeviceKeyStore.View.Self =
    new DeviceKeyStore.View.Self {
      override def exists(ownDevices: Seq[Device.Id], forDevice: Device.Id): Future[Boolean] =
        if (ownDevices.contains(forDevice)) {
          store.exists(forDevice)
        } else {
          Future.failed(
            new IllegalArgumentException(
              s"Expected to retrieve own device key but key for device [${forDevice.toString}] found"
            )
          )
        }

      override def get(ownDevices: Seq[Device.Id], forDevice: Device.Id): Future[Option[DeviceKey]] =
        if (ownDevices.contains(forDevice)) {
          store.get(forDevice)
        } else {
          Future.failed(
            new IllegalArgumentException(
              s"Expected to retrieve own device key but key for device [${forDevice.toString}] found"
            )
          )
        }

      override def list(ownDevices: Seq[Device.Id]): Future[Seq[DeviceKey]] =
        store.list().map(_.filter(c => ownDevices.contains(c.device)))
    }

  final def manage(): DeviceKeyStore.Manage.Privileged =
    new DeviceKeyStore.Manage.Privileged {
      override def delete(forDevice: Device.Id): Future[Boolean] =
        store.delete(forDevice)
    }

  final def manageSelf(): DeviceKeyStore.Manage.Self =
    new DeviceKeyStore.Manage.Self {
      override def put(ownDevices: Seq[Device.Id], key: DeviceKey): Future[Done] =
        if (ownDevices.contains(key.device)) {
          store.put(key)
        } else {
          Future.failed(
            AuthorizationFailure(
              s"Expected to put own device key but key for device [${key.device.toString}] provided"
            )
          )
        }

      override def delete(ownDevices: Seq[Device.Id], forDevice: Device.Id): Future[Boolean] =
        if (ownDevices.contains(forDevice)) {
          store.delete(forDevice)
        } else {
          Future.failed(
            AuthorizationFailure(
              s"Expected to delete own device key but key for device [${forDevice.toString}] provided"
            )
          )
        }
    }
}

object DeviceKeyStore {
  object View {
    sealed trait Privileged extends Resource {
      def get(forDevice: Device.Id): Future[Option[DeviceKey]]
      def list(): Future[Seq[DeviceKey]]
      override def requiredPermission: Permission = Permission.View.Privileged
    }

    sealed trait Self extends Resource {
      def exists(ownDevices: Seq[Device.Id], forDevice: Device.Id): Future[Boolean]
      def get(ownDevices: Seq[Device.Id], forDevice: Device.Id): Future[Option[DeviceKey]]
      def list(ownDevices: Seq[Device.Id]): Future[Seq[DeviceKey]]
      override def requiredPermission: Permission = Permission.View.Self
    }
  }

  object Manage {
    sealed trait Privileged extends Resource {
      def delete(forDevice: Device.Id): Future[Boolean]
      override def requiredPermission: Permission = Permission.Manage.Privileged
    }

    sealed trait Self extends Resource {
      def put(ownDevices: Seq[Device.Id], key: DeviceKey): Future[Done]
      def delete(ownDevices: Seq[Device.Id], forDevice: Device.Id): Future[Boolean]
      override def requiredPermission: Permission = Permission.Manage.Self
    }
  }

  def apply(
    backend: KeyValueBackend[Device.Id, DeviceKey]
  )(implicit system: ActorSystem[SpawnProtocol.Command]): DeviceKeyStore =
    new DeviceKeyStore {
      override protected implicit def ec: ExecutionContext = system.executionContext

      override protected def put(key: DeviceKey): Future[Done] =
        backend.put(key.device, key)

      override protected def delete(forDevice: Device.Id): Future[Boolean] =
        backend.delete(forDevice)

      override protected def exists(forDevice: Device.Id): Future[Boolean] =
        backend.contains(forDevice)

      override protected def get(forDevice: Device.Id): Future[Option[DeviceKey]] =
        backend.get(forDevice)

      override protected def list(): Future[Seq[DeviceKey]] =
        backend.entries.map(_.values.map(_.copy(value = ByteString.empty)).toSeq)
    }
}
