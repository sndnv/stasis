package stasis.server.persistence.users

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

import org.apache.pekko.Done

import stasis.layers.persistence.Store
import stasis.server.security.CurrentUser
import stasis.server.security.Resource
import stasis.shared.model.users.User
import stasis.shared.security.Permission

trait UserStore extends Store { store =>
  protected implicit def ec: ExecutionContext

  protected[users] def put(user: User): Future[Done]
  protected[users] def delete(user: User.Id): Future[Boolean]
  protected[users] def get(user: User.Id): Future[Option[User]]
  protected[users] def list(): Future[Seq[User]]
  protected[users] def generateSalt(): String

  final def view(): UserStore.View.Privileged =
    new UserStore.View.Privileged {
      override def get(user: User.Id): Future[Option[User]] =
        store.get(user)

      override def list(): Future[Seq[User]] =
        store.list()
    }

  final def viewSelf(): UserStore.View.Self =
    new UserStore.View.Self {
      override def get(self: CurrentUser): Future[Option[User]] =
        store.get(self.id)
    }

  final def manage(): UserStore.Manage.Privileged =
    new UserStore.Manage.Privileged {
      override def put(user: User): Future[Done] =
        store.put(user)

      override def delete(user: User.Id): Future[Boolean] =
        store.delete(user)

      override def generateSalt(): String =
        store.generateSalt()
    }

  final def manageSelf(): UserStore.Manage.Self =
    new UserStore.Manage.Self {
      override def resetSalt(self: CurrentUser): Future[String] =
        store.get(self.id).flatMap {
          case Some(user) if user.active =>
            val updated = generateSalt()
            store.put(user.copy(salt = updated)).map { _ => updated }

          case Some(_) =>
            Future.failed(new IllegalArgumentException(s"User [${self.id.toString}] is not active"))

          case None =>
            Future.failed(new IllegalArgumentException(s"Expected user [${self.id.toString}] not found"))
        }

      override def deactivate(self: CurrentUser): Future[Done] =
        store.get(self.id).flatMap {
          case Some(user) if user.active => store.put(user.copy(active = false))
          case Some(_) => Future.failed(new IllegalArgumentException(s"User [${self.id.toString}] is not active"))
          case None    => Future.failed(new IllegalArgumentException(s"Expected user [${self.id.toString}] not found"))
        }
    }
}

object UserStore {
  object View {
    sealed trait Privileged extends Resource {
      def get(user: User.Id): Future[Option[User]]
      def list(): Future[Seq[User]]
      override def requiredPermission: Permission = Permission.View.Privileged
    }

    sealed trait Self extends Resource {
      def get(self: CurrentUser): Future[Option[User]]
      override def requiredPermission: Permission = Permission.View.Self
    }
  }

  object Manage {
    sealed trait Privileged extends Resource {
      def put(user: User): Future[Done]
      def delete(user: User.Id): Future[Boolean]
      def generateSalt(): String
      override def requiredPermission: Permission = Permission.Manage.Privileged
    }

    sealed trait Self extends Resource {
      def resetSalt(self: CurrentUser): Future[String]
      def deactivate(self: CurrentUser): Future[Done]
      override def requiredPermission: Permission = Permission.Manage.Self
    }
  }
}
