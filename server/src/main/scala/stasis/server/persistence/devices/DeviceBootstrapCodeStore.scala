package stasis.server.persistence.devices

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

import org.apache.pekko.Done

import stasis.layers.persistence.Store
import stasis.server.security.CurrentUser
import stasis.server.security.Resource
import stasis.server.security.exceptions.AuthorizationFailure
import stasis.shared.model.devices.DeviceBootstrapCode
import stasis.shared.security.Permission

trait DeviceBootstrapCodeStore extends Store { store =>
  protected implicit def ec: ExecutionContext

  protected[devices] def put(code: DeviceBootstrapCode): Future[Done]
  protected[devices] def delete(code: String): Future[Boolean]
  protected[devices] def delete(code: DeviceBootstrapCode.Id): Future[Boolean]
  protected[devices] def consume(code: String): Future[Option[DeviceBootstrapCode]]
  protected[devices] def get(code: String): Future[Option[DeviceBootstrapCode]]
  protected[devices] def get(code: DeviceBootstrapCode.Id): Future[Option[DeviceBootstrapCode]]
  protected[devices] def list(): Future[Seq[DeviceBootstrapCode]]

  final def view(): DeviceBootstrapCodeStore.View.Privileged =
    new DeviceBootstrapCodeStore.View.Privileged {
      override def get(code: String): Future[Option[DeviceBootstrapCode]] =
        store.get(code)

      override def list(): Future[Seq[DeviceBootstrapCode]] =
        store.list()
    }

  final def viewSelf(): DeviceBootstrapCodeStore.View.Self =
    new DeviceBootstrapCodeStore.View.Self {
      override def get(self: CurrentUser, code: String): Future[Option[DeviceBootstrapCode]] =
        store.get(code).flatMap {
          case Some(c) if c.owner == self.id =>
            Future.successful(Some(c))

          case Some(c) =>
            Future.failed(
              AuthorizationFailure(
                s"Expected to retrieve own device bootstrap code but code for user [${c.owner.toString}] found"
              )
            )

          case None =>
            Future.successful(None)
        }

      override def list(self: CurrentUser): Future[Seq[DeviceBootstrapCode]] =
        store.list().map(_.filter(_.owner == self.id))
    }

  final def manage(): DeviceBootstrapCodeStore.Manage.Privileged =
    new DeviceBootstrapCodeStore.Manage.Privileged {
      override def put(code: DeviceBootstrapCode): Future[Done] =
        store.put(code)

      override def delete(code: DeviceBootstrapCode.Id): Future[Boolean] =
        store.delete(code)

      override def consume(code: String): Future[Option[DeviceBootstrapCode]] =
        store.consume(code)
    }

  final def manageSelf(): DeviceBootstrapCodeStore.Manage.Self =
    new DeviceBootstrapCodeStore.Manage.Self {
      override def put(self: CurrentUser, code: DeviceBootstrapCode): Future[Done] =
        if (code.owner == self.id) {
          store.put(code)
        } else {
          Future.failed(
            AuthorizationFailure(
              s"Expected to put own device bootstrap code but code for user [${code.owner.toString}] provided"
            )
          )
        }

      override def delete(self: CurrentUser, code: DeviceBootstrapCode.Id): Future[Boolean] =
        store.get(code).flatMap {
          case Some(c) if c.owner == self.id =>
            store.delete(code)

          case Some(c) =>
            Future.failed(
              AuthorizationFailure(
                s"Expected to delete own device bootstrap code but code for user [${c.owner.toString}] found"
              )
            )

          case None =>
            Future.successful(false)
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
      def get(self: CurrentUser, code: String): Future[Option[DeviceBootstrapCode]]
      def list(self: CurrentUser): Future[Seq[DeviceBootstrapCode]]
      override def requiredPermission: Permission = Permission.View.Self
    }
  }

  object Manage {
    sealed trait Privileged extends Resource {
      def put(code: DeviceBootstrapCode): Future[Done]
      def delete(code: DeviceBootstrapCode.Id): Future[Boolean]
      def consume(code: String): Future[Option[DeviceBootstrapCode]]
      override def requiredPermission: Permission = Permission.Manage.Privileged
    }

    sealed trait Self extends Resource {
      def put(self: CurrentUser, code: DeviceBootstrapCode): Future[Done]
      def delete(self: CurrentUser, code: DeviceBootstrapCode.Id): Future[Boolean]
      override def requiredPermission: Permission = Permission.Manage.Self
    }
  }
}
