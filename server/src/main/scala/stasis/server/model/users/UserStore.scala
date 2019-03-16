package stasis.server.model.users

import akka.Done
import stasis.server.security.{CurrentUser, Permission, Resource}

import scala.concurrent.{ExecutionContext, Future}

trait UserStore { store =>
  protected implicit def ec: ExecutionContext

  protected def create(user: User): Future[Done]
  protected def update(user: User): Future[Done]
  protected def delete(user: User.Id): Future[Boolean]
  protected def get(user: User.Id): Future[Option[User]]
  protected def list(): Future[Map[User.Id, User]]

  final def view(): UserStore.View.Privileged =
    new UserStore.View.Privileged {
      override def get(user: User.Id): Future[Option[User]] =
        store.get(user)

      override def list(): Future[Map[User.Id, User]] =
        store.list()
    }

  final def viewSelf(): UserStore.View.Self =
    new UserStore.View.Self {
      override def get(self: CurrentUser): Future[Option[User]] =
        store.get(self.id)
    }

  final def manage(): UserStore.Manage.Privileged =
    new UserStore.Manage.Privileged {
      override def create(user: User): Future[Done] =
        store.create(user)

      override def update(user: User): Future[Done] =
        store.update(user)

      override def delete(user: User.Id): Future[Boolean] =
        store.delete(user)
    }

  final def manageSelf(): UserStore.Manage.Self =
    new UserStore.Manage.Self {
      override def deactivate(self: CurrentUser): Future[Done] =
        store.get(self.id).flatMap {
          case Some(user) => store.update(user.copy(isActive = false))
          case None       => Future.failed(new IllegalArgumentException(s"Expected user [${self.id}] not found"))
        }
    }
}

object UserStore {
  object View {
    sealed trait Privileged extends Resource {
      def get(user: User.Id): Future[Option[User]]
      def list(): Future[Map[User.Id, User]]
      override def requiredPermission: Permission = Permission.View.Privileged
    }

    sealed trait Self extends Resource {
      def get(self: CurrentUser): Future[Option[User]]
      override def requiredPermission: Permission = Permission.View.Self
    }
  }

  object Manage {
    sealed trait Privileged extends Resource {
      def create(user: User): Future[Done]
      def update(user: User): Future[Done]
      def delete(user: User.Id): Future[Boolean]
      override def requiredPermission: Permission = Permission.Manage.Privileged
    }

    sealed trait Self extends Resource {
      def deactivate(self: CurrentUser): Future[Done]
      override def requiredPermission: Permission = Permission.Manage.Self
    }
  }
}
