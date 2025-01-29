package stasis.server.persistence.devices

import java.time.Instant

import scala.concurrent.Future

import org.apache.pekko.Done

import stasis.core.commands.proto.Command
import stasis.core.persistence.commands.CommandStore
import stasis.layers.persistence.Store
import stasis.layers.persistence.migration.Migration
import stasis.server.security.Resource
import stasis.server.security.exceptions.AuthorizationFailure
import stasis.shared.model.devices.Device
import stasis.shared.security.Permission

trait DeviceCommandStore extends Store { store =>
  protected def put(command: Command): Future[Done]
  protected def delete(sequenceId: Long): Future[Boolean]
  protected def truncate(olderThan: Instant): Future[Done]
  protected def list(): Future[Seq[Command]]
  protected def list(forDevice: Device.Id): Future[Seq[Command]]
  protected def list(forDevice: Device.Id, lastSequenceId: Long): Future[Seq[Command]]

  final def view(): DeviceCommandStore.View.Service =
    new DeviceCommandStore.View.Service {
      override def list(): Future[Seq[Command]] =
        store.list()

      override def list(forDevice: Device.Id): Future[Seq[Command]] =
        store.list(forDevice)
    }

  final def viewSelf(): DeviceCommandStore.View.Self =
    new DeviceCommandStore.View.Self {
      override def list(ownDevices: Seq[Device.Id], forDevice: Device.Id): Future[Seq[Command]] =
        if (ownDevices.contains(forDevice)) {
          store.list(forDevice)
        } else {
          Future.failed(
            AuthorizationFailure(
              s"Expected to retrieve commands for own device but commands for device [${forDevice.toString}] requested"
            )
          )
        }

      override def list(ownDevices: Seq[Device.Id], forDevice: Device.Id, lastSequenceId: Long): Future[Seq[Command]] =
        if (ownDevices.contains(forDevice)) {
          store.list(forDevice, lastSequenceId)
        } else {
          Future.failed(
            AuthorizationFailure(
              s"Expected to retrieve commands for own device but commands for device [${forDevice.toString}] requested"
            )
          )
        }
    }

  final def manage(): DeviceCommandStore.Manage.Service =
    new DeviceCommandStore.Manage.Service {
      override def put(command: Command): Future[Done] =
        store.put(command)

      override def delete(sequenceId: Long): Future[Boolean] =
        store.delete(sequenceId)

      override def truncate(olderThan: Instant): Future[Done] =
        store.truncate(olderThan)
    }

  final def manageSelf(): DeviceCommandStore.Manage.Self =
    new DeviceCommandStore.Manage.Self {
      override def put(ownDevices: Seq[Device.Id], command: Command): Future[Done] =
        command.target match {
          case Some(targetDevice) =>
            if (ownDevices.contains(targetDevice)) {
              store.put(command)
            } else {
              Future.failed(
                AuthorizationFailure(
                  s"Expected to put command for own device but command for device [${targetDevice.toString}] provided"
                )
              )
            }

          case None =>
            Future.failed(
              AuthorizationFailure(
                "Expected to put command for own device but command without a target provided"
              )
            )
        }
    }
}

object DeviceCommandStore {
  object Manage {
    sealed trait Service extends Resource {
      def put(command: Command): Future[Done]
      def delete(sequenceId: Long): Future[Boolean]
      def truncate(olderThan: Instant): Future[Done]
      override def requiredPermission: Permission = Permission.Manage.Service
    }

    sealed trait Self extends Resource {
      def put(ownDevices: Seq[Device.Id], command: Command): Future[Done]
      override def requiredPermission: Permission = Permission.Manage.Self
    }
  }

  object View {
    sealed trait Service extends Resource {
      def list(): Future[Seq[Command]]
      def list(forDevice: Device.Id): Future[Seq[Command]]
      override def requiredPermission: Permission = Permission.View.Service
    }

    sealed trait Self extends Resource {
      def list(ownDevices: Seq[Device.Id], forDevice: Device.Id): Future[Seq[Command]]
      def list(ownDevices: Seq[Device.Id], forDevice: Device.Id, lastSequenceId: Long): Future[Seq[Command]]
      override def requiredPermission: Permission = Permission.View.Self
    }
  }

  def apply(store: CommandStore): DeviceCommandStore =
    new DeviceCommandStore {
      override def name(): String = store.name()

      override def migrations(): Seq[Migration] = store.migrations()

      override def init(): Future[Done] = store.init()

      override def drop(): Future[Done] = store.drop()

      override def put(command: Command): Future[Done] =
        store.put(command)

      override protected def delete(sequenceId: Long): Future[Boolean] =
        store.delete(sequenceId)

      override def truncate(olderThan: Instant): Future[Done] =
        store.truncate(olderThan)

      override def list(): Future[Seq[Command]] =
        store.list()

      override def list(forDevice: Device.Id): Future[Seq[Command]] =
        store.list(forDevice)

      override def list(forDevice: Device.Id, lastSequenceId: Long): Future[Seq[Command]] =
        store.list(forDevice, lastSequenceId)
    }
}
