package stasis.server.model.users

import java.util.concurrent.ThreadLocalRandom

import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.util.Random

import org.apache.pekko.Done

import stasis.layers.persistence.KeyValueStore
import stasis.server.security.CurrentUser
import stasis.server.security.Resource
import stasis.shared.model.users.User
import stasis.shared.security.Permission

trait UserStore { store =>
  protected implicit def ec: ExecutionContext

  protected def create(user: User): Future[Done]
  protected def update(user: User): Future[Done]
  protected def delete(user: User.Id): Future[Boolean]
  protected def get(user: User.Id): Future[Option[User]]
  protected def list(): Future[Map[User.Id, User]]
  protected def generateSalt(): String

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

      override def generateSalt(): String =
        store.generateSalt()
    }

  final def manageSelf(): UserStore.Manage.Self =
    new UserStore.Manage.Self {
      override def resetSalt(self: CurrentUser): Future[String] =
        store.get(self.id).flatMap {
          case Some(user) if user.active =>
            val updated = generateSalt()
            store.update(user.copy(salt = updated)).map { _ => updated }

          case Some(_) =>
            Future.failed(new IllegalArgumentException(s"User [${self.id.toString}] is not active"))

          case None =>
            Future.failed(new IllegalArgumentException(s"Expected user [${self.id.toString}] not found"))
        }

      override def deactivate(self: CurrentUser): Future[Done] =
        store.get(self.id).flatMap {
          case Some(user) if user.active => store.update(user.copy(active = false))
          case Some(_) => Future.failed(new IllegalArgumentException(s"User [${self.id.toString}] is not active"))
          case None    => Future.failed(new IllegalArgumentException(s"Expected user [${self.id.toString}] not found"))
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
      def generateSalt(): String
      override def requiredPermission: Permission = Permission.Manage.Privileged
    }

    sealed trait Self extends Resource {
      def resetSalt(self: CurrentUser): Future[String]
      def deactivate(self: CurrentUser): Future[Done]
      override def requiredPermission: Permission = Permission.Manage.Self
    }
  }

  def apply(
    userSaltSize: Int,
    backend: KeyValueStore[User.Id, User]
  )(implicit ctx: ExecutionContext): UserStore =
    new UserStore {
      override implicit protected def ec: ExecutionContext = ctx
      override protected def create(user: User): Future[Done] = backend.put(user.id, user)
      override protected def update(user: User): Future[Done] = backend.put(user.id, user)
      override protected def delete(user: User.Id): Future[Boolean] = backend.delete(user)
      override protected def get(user: User.Id): Future[Option[User]] = backend.get(user)
      override protected def list(): Future[Map[User.Id, User]] = backend.entries

      override protected def generateSalt(): String = {
        val rnd: Random = ThreadLocalRandom.current()
        rnd.alphanumeric.take(userSaltSize).mkString
      }
    }
}
