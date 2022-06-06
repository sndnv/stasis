package stasis.test.specs.unit.server.model.users

import akka.Done
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorSystem, Behavior, SpawnProtocol}
import stasis.core.telemetry.TelemetryContext
import stasis.server.security.CurrentUser
import stasis.shared.model.users.User
import stasis.shared.security.Permission
import stasis.test.specs.unit.AsyncUnitSpec
import stasis.test.specs.unit.core.telemetry.MockTelemetryContext
import stasis.test.specs.unit.server.model.mocks.MockUserStore

import scala.util.control.NonFatal

class UserStoreSpec extends AsyncUnitSpec {
  "A UserStore" should "provide a view resource (privileged)" in {
    val store = MockUserStore()
    store.view().requiredPermission should be(Permission.View.Privileged)
  }

  it should "return existing users via view resource (privileged)" in {
    val store = MockUserStore()

    store.manage().create(mockUser).await

    store.view().get(mockUser.id).map(result => result should be(Some(mockUser)))
  }

  it should "return a list of users via view resource (privileged)" in {
    val store = MockUserStore()

    store.manage().create(mockUser).await
    store.manage().create(mockUser.copy(id = User.generateId())).await
    store.manage().create(mockUser.copy(id = User.generateId())).await

    store.view().list().map { result =>
      result.size should be(3)
      result.get(mockUser.id) should be(Some(mockUser))
    }
  }

  it should "provide a view resource (self)" in {
    val store = MockUserStore()
    store.viewSelf().requiredPermission should be(Permission.View.Self)
  }

  it should "return existing users for current user via view resource (self)" in {
    val store = MockUserStore()

    val ownUser = mockUser.copy(id = self.id)
    store.manage().create(ownUser).await

    store.viewSelf().get(self).map(result => result should be(Some(ownUser)))
  }

  it should "provide management resource (privileged)" in {
    val store = MockUserStore()
    store.manage().requiredPermission should be(Permission.Manage.Privileged)
  }

  it should "allow creating users via management resource (privileged)" in {
    val store = MockUserStore()

    for {
      createResult <- store.manage().create(mockUser)
      getResult <- store.view().get(mockUser.id)
    } yield {
      createResult should be(Done)
      getResult should be(Some(mockUser))
    }
  }

  it should "allow updating users via management resource (privileged)" in {
    val store = MockUserStore()

    val updatedActive = false

    for {
      createResult <- store.manage().create(mockUser)
      getResult <- store.view().get(mockUser.id)
      updateResult <- store.manage().update(mockUser.copy(active = updatedActive))
      updatedGetResult <- store.view().get(mockUser.id)
    } yield {
      createResult should be(Done)
      getResult should be(Some(mockUser))
      updateResult should be(Done)
      updatedGetResult should be(Some(mockUser.copy(active = updatedActive)))
    }
  }

  it should "allow deleting users via management resource (privileged)" in {
    val store = MockUserStore()

    for {
      createResult <- store.manage().create(mockUser)
      getResult <- store.view().get(mockUser.id)
      deleteResult <- store.manage().delete(mockUser.id)
      deletedGetResult <- store.view().get(mockUser.id)
    } yield {
      createResult should be(Done)
      getResult should be(Some(mockUser))
      deleteResult should be(true)
      deletedGetResult should be(None)
    }
  }

  it should "allow generating salt values for users (privileged)" in {
    val expectedSaltSize = 12
    val store = MockUserStore(userSaltSize = expectedSaltSize)

    val salt = store.manage().generateSalt()
    salt should have length expectedSaltSize.toLong
  }

  it should "provide management resource (self)" in {
    val store = MockUserStore()
    store.manageSelf().requiredPermission should be(Permission.Manage.Self)
  }

  it should "allow deactivating own user via management resource (self)" in {
    val store = MockUserStore()

    val ownUser = mockUser.copy(id = self.id)

    for {
      createResult <- store.manage().create(ownUser)
      getResult <- store.viewSelf().get(self)
      updateResult <- store.manageSelf().deactivate(self)
      updatedGetResult <- store.viewSelf().get(self)
    } yield {
      createResult should be(Done)
      getResult should be(Some(ownUser))
      updateResult should be(Done)
      updatedGetResult should be(Some(ownUser.copy(active = false)))
    }
  }

  it should "fail to deactivate an inactive user via management resource (self)" in {
    val store = MockUserStore()

    val ownUser = mockUser.copy(id = self.id, active = false)

    for {
      createResult <- store.manage().create(ownUser)
      getResult <- store.viewSelf().get(self)
      updateResult <- store.manageSelf().deactivate(self).failed
    } yield {
      createResult should be(Done)
      getResult should be(Some(ownUser))
      updateResult.getMessage should be(s"User [${self.id}] is not active")
    }
  }

  it should "fail to deactivate a missing user via management resource (self)" in {
    val store = MockUserStore()

    store
      .manageSelf()
      .deactivate(self)
      .map { response =>
        fail(s"Received unexpected response from store: [$response]")
      }
      .recover { case NonFatal(e) =>
        e.getMessage should be(
          s"Expected user [${self.id}] not found"
        )
      }
  }

  it should "allow resetting own user password salt via management resource (self)" in {
    val store = MockUserStore()

    val ownUser = mockUser.copy(id = self.id)

    for {
      createResult <- store.manage().create(ownUser)
      getResult <- store.viewSelf().get(self)
      updatedSalt <- store.manageSelf().resetSalt(self)
      updatedGetResult <- store.viewSelf().get(self)
    } yield {
      createResult should be(Done)
      getResult should be(Some(ownUser))
      updatedSalt should not be (ownUser.salt)
      updatedSalt should be(updatedGetResult.map(_.salt).getOrElse("invalid"))
      updatedGetResult should be(Some(ownUser.copy(salt = updatedSalt)))
    }
  }

  it should "fail to reset password salt for an inactive user via management resource (self)" in {
    val store = MockUserStore()

    val ownUser = mockUser.copy(id = self.id, active = false)

    for {
      createResult <- store.manage().create(ownUser)
      getResult <- store.viewSelf().get(self)
      updateResult <- store.manageSelf().resetSalt(self).failed
    } yield {
      createResult should be(Done)
      getResult should be(Some(ownUser))
      updateResult.getMessage should be(s"User [${self.id}] is not active")
    }
  }

  it should "fail to reset password salt for a missing user via management resource (self)" in {
    val store = MockUserStore()

    store
      .manageSelf()
      .resetSalt(self)
      .map { response =>
        fail(s"Received unexpected response from store: [$response]")
      }
      .recover { case NonFatal(e) =>
        e.getMessage should be(
          s"Expected user [${self.id}] not found"
        )
      }
  }

  private implicit val typedSystem: ActorSystem[SpawnProtocol.Command] = ActorSystem(
    Behaviors.setup(_ => SpawnProtocol()): Behavior[SpawnProtocol.Command],
    "UserStoreSpec"
  )

  private implicit val telemetry: TelemetryContext = MockTelemetryContext()

  private val mockUser = User(
    id = User.generateId(),
    salt = "test-salt",
    active = true,
    limits = None,
    permissions = Set.empty
  )

  private val self = CurrentUser(User.generateId())
}
