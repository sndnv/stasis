package stasis.test.specs.unit.server.api.routes

import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.Success

import org.apache.pekko.actor.typed.ActorSystem
import org.apache.pekko.actor.typed.scaladsl.Behaviors
import org.apache.pekko.http.scaladsl.marshalling.Marshal
import org.apache.pekko.http.scaladsl.model.RequestEntity
import org.apache.pekko.http.scaladsl.model.StatusCodes
import org.apache.pekko.http.scaladsl.server.Route
import org.apache.pekko.http.scaladsl.testkit.ScalatestRouteTest
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import stasis.layers.telemetry.TelemetryContext
import stasis.server.api.routes.RoutesContext
import stasis.server.api.routes.Users
import stasis.server.model.users.UserStore
import stasis.server.security.CurrentUser
import stasis.server.security.ResourceProvider
import stasis.server.security.users.UserCredentialsManager
import stasis.shared.api.requests._
import stasis.shared.api.responses.CreatedUser
import stasis.shared.api.responses.DeletedUser
import stasis.shared.api.responses.UpdatedUserSalt
import stasis.shared.model.users.User
import stasis.shared.security.Permission
import stasis.test.specs.unit.AsyncUnitSpec
import stasis.test.specs.unit.core.telemetry.MockTelemetryContext
import stasis.test.specs.unit.server.Secrets
import stasis.test.specs.unit.server.model.mocks.MockUserStore
import stasis.test.specs.unit.server.security.mocks.MockResourceProvider
import stasis.test.specs.unit.server.security.mocks.MockUserCredentialsManager

class UsersSpec extends AsyncUnitSpec with ScalatestRouteTest with Secrets {
  import com.github.pjfanning.pekkohttpplayjson.PlayJsonSupport._

  import stasis.shared.api.Formats._

  "Users routes (full permissions)" should "respond with all users" in withRetry {
    val fixtures = new TestFixtures {}
    Future.sequence(users.map(fixtures.userStore.manage().create)).await

    Get("/") ~> fixtures.routes ~> check {
      status should be(StatusCodes.OK)
      responseAs[Seq[User]] should contain theSameElementsAs users
    }
  }

  they should "create new users (with authentication password hashing)" in withRetry {
    val fixtures = new TestFixtures {}
    Post("/").withEntity(createRequest) ~> fixtures.routes ~> check {
      status should be(StatusCodes.OK)

      fixtures.userStore
        .view()
        .get(entityAs[CreatedUser].user)
        .map { user =>
          user.isDefined should be(true)

          fixtures.credentialsManager.resourceOwnersCreated should be(1)
          fixtures.credentialsManager.resourceOwnersActivated should be(0)
          fixtures.credentialsManager.resourceOwnersDeactivated should be(0)
          fixtures.credentialsManager.resourceOwnerPasswordsSet should be(0)
          fixtures.credentialsManager.notFound should be(0)
          fixtures.credentialsManager.conflicts should be(0)
          fixtures.credentialsManager.failures should be(0)

          fixtures.credentialsManager.latestPassword should not be empty
          fixtures.credentialsManager.latestPassword should not be createRequest.rawPassword
        }
    }
  }

  they should "create new users (without authentication password hashing)" in withRetry {
    val fixtures = new TestFixtures {
      override lazy val hashAuthenticationPasswords: Boolean = false
    }

    Post("/").withEntity(createRequest) ~> fixtures.routes ~> check {
      status should be(StatusCodes.OK)

      fixtures.userStore
        .view()
        .get(entityAs[CreatedUser].user)
        .map { user =>
          user.isDefined should be(true)

          fixtures.credentialsManager.resourceOwnersCreated should be(1)
          fixtures.credentialsManager.resourceOwnersActivated should be(0)
          fixtures.credentialsManager.resourceOwnersDeactivated should be(0)
          fixtures.credentialsManager.resourceOwnerPasswordsSet should be(0)
          fixtures.credentialsManager.notFound should be(0)
          fixtures.credentialsManager.conflicts should be(0)
          fixtures.credentialsManager.failures should be(0)

          fixtures.credentialsManager.latestPassword should not be empty
          fixtures.credentialsManager.latestPassword should be(createRequest.rawPassword)
        }
    }
  }

  they should "handle resource owner creation failures (not found)" in withRetry {
    val fixtures = new TestFixtures {
      override lazy val credentialsManager: MockUserCredentialsManager =
        MockUserCredentialsManager(
          withResult = Success(UserCredentialsManager.Result.NotFound("test-message"))
        )
    }
    Post("/").withEntity(createRequest) ~> fixtures.routes ~> check {
      status should be(StatusCodes.NotFound)

      fixtures.userStore
        .view()
        .list()
        .map { users =>
          users shouldBe empty

          fixtures.credentialsManager.resourceOwnersCreated should be(1)
          fixtures.credentialsManager.resourceOwnersActivated should be(0)
          fixtures.credentialsManager.resourceOwnersDeactivated should be(0)
          fixtures.credentialsManager.resourceOwnerPasswordsSet should be(0)
          fixtures.credentialsManager.notFound should be(1)
          fixtures.credentialsManager.conflicts should be(0)
          fixtures.credentialsManager.failures should be(0)
        }
    }
  }

  they should "handle resource owner creation failures (conflict)" in withRetry {
    val fixtures = new TestFixtures {
      override lazy val credentialsManager: MockUserCredentialsManager =
        MockUserCredentialsManager(
          withResult = Success(UserCredentialsManager.Result.Conflict("test-message"))
        )
    }
    Post("/").withEntity(createRequest) ~> fixtures.routes ~> check {
      status should be(StatusCodes.Conflict)

      fixtures.userStore
        .view()
        .list()
        .map { users =>
          users shouldBe empty

          fixtures.credentialsManager.resourceOwnersCreated should be(1)
          fixtures.credentialsManager.resourceOwnersActivated should be(0)
          fixtures.credentialsManager.resourceOwnersDeactivated should be(0)
          fixtures.credentialsManager.resourceOwnerPasswordsSet should be(0)
          fixtures.credentialsManager.notFound should be(0)
          fixtures.credentialsManager.conflicts should be(1)
          fixtures.credentialsManager.failures should be(0)
        }
    }
  }

  they should "respond with existing users" in withRetry {
    val fixtures = new TestFixtures {}

    fixtures.userStore.manage().create(users.head).await

    Get(s"/${users.head.id}") ~> fixtures.routes ~> check {
      status should be(StatusCodes.OK)
      responseAs[User] should be(users.head)
    }
  }

  they should "fail if a user is missing" in withRetry {
    val fixtures = new TestFixtures {}
    Get(s"/${User.generateId()}") ~> fixtures.routes ~> check {
      status should be(StatusCodes.NotFound)
    }
  }

  they should "update existing users' limits" in withRetry {
    val fixtures = new TestFixtures {}
    fixtures.userStore.manage().create(users.head).await

    Put(s"/${users.head.id}/limits")
      .withEntity(updateLimitsRequest) ~> fixtures.routes ~> check {
      status should be(StatusCodes.OK)

      fixtures.userStore
        .view()
        .get(users.head.id)
        .map(_.map(_.limits) should be(Some(updateLimitsRequest.limits)))
    }
  }

  they should "update existing users' permissions" in withRetry {
    val fixtures = new TestFixtures {}
    fixtures.userStore.manage().create(users.head).await

    Put(s"/${users.head.id}/permissions")
      .withEntity(updatePermissionsRequest) ~> fixtures.routes ~> check {
      status should be(StatusCodes.OK)

      fixtures.userStore
        .view()
        .get(users.head.id)
        .map(_.map(_.permissions.size).getOrElse(0) should be(updatePermissionsRequest.permissions.size))
    }
  }

  they should "update existing users' state (to active)" in withRetry {
    val fixtures = new TestFixtures {}
    fixtures.userStore.manage().create(users.head).await

    val updateStateRequest = UpdateUserState(
      active = true
    )

    Put(s"/${users.head.id}/state")
      .withEntity(updateStateRequest.copy()) ~> fixtures.routes ~> check {
      status should be(StatusCodes.OK)

      fixtures.userStore
        .view()
        .get(users.head.id)
        .map { user =>
          user.map(_.active) should be(Some(updateStateRequest.active))

          fixtures.credentialsManager.resourceOwnersCreated should be(0)
          fixtures.credentialsManager.resourceOwnersActivated should be(1)
          fixtures.credentialsManager.resourceOwnersDeactivated should be(0)
          fixtures.credentialsManager.resourceOwnerPasswordsSet should be(0)
          fixtures.credentialsManager.notFound should be(0)
          fixtures.credentialsManager.conflicts should be(0)
          fixtures.credentialsManager.failures should be(0)
        }
    }
  }

  they should "update existing users' state (to inactive)" in withRetry {
    val fixtures = new TestFixtures {}
    fixtures.userStore.manage().create(users.head).await

    val updateStateRequest = UpdateUserState(
      active = false
    )

    Put(s"/${users.head.id}/state")
      .withEntity(updateStateRequest) ~> fixtures.routes ~> check {
      status should be(StatusCodes.OK)

      fixtures.userStore
        .view()
        .get(users.head.id)
        .map { user =>
          user.map(_.active) should be(Some(updateStateRequest.active))

          fixtures.credentialsManager.resourceOwnersCreated should be(0)
          fixtures.credentialsManager.resourceOwnersActivated should be(0)
          fixtures.credentialsManager.resourceOwnersDeactivated should be(1)
          fixtures.credentialsManager.resourceOwnerPasswordsSet should be(0)
          fixtures.credentialsManager.notFound should be(0)
          fixtures.credentialsManager.conflicts should be(0)
          fixtures.credentialsManager.failures should be(0)
        }
    }
  }

  they should "handle resource owner activation/deactivation failures when updating user state (not found)" in withRetry {
    val fixtures = new TestFixtures {
      override lazy val credentialsManager: MockUserCredentialsManager =
        MockUserCredentialsManager(
          withResult = Success(UserCredentialsManager.Result.NotFound("test-message"))
        )
    }

    fixtures.userStore.manage().create(users.head).await

    val updateStateRequest = UpdateUserState(
      active = false
    )

    Put(s"/${users.head.id}/state")
      .withEntity(updateStateRequest) ~> fixtures.routes ~> check {
      status should be(StatusCodes.NotFound)

      fixtures.userStore
        .view()
        .get(users.head.id)
        .map { user =>
          user should be(users.headOption)

          fixtures.credentialsManager.resourceOwnersCreated should be(0)
          fixtures.credentialsManager.resourceOwnersActivated should be(0)
          fixtures.credentialsManager.resourceOwnersDeactivated should be(1)
          fixtures.credentialsManager.resourceOwnerPasswordsSet should be(0)
          fixtures.credentialsManager.notFound should be(1)
          fixtures.credentialsManager.conflicts should be(0)
          fixtures.credentialsManager.failures should be(0)
        }
    }
  }

  they should "handle resource owner activation/deactivation failures when updating user state (conflict)" in withRetry {
    val fixtures = new TestFixtures {
      override lazy val credentialsManager: MockUserCredentialsManager =
        MockUserCredentialsManager(
          withResult = Success(UserCredentialsManager.Result.Conflict("test-message"))
        )
    }

    fixtures.userStore.manage().create(users.head).await

    val updateStateRequest = UpdateUserState(
      active = false
    )

    Put(s"/${users.head.id}/state")
      .withEntity(updateStateRequest) ~> fixtures.routes ~> check {
      status should be(StatusCodes.Conflict)

      fixtures.userStore
        .view()
        .get(users.head.id)
        .map { user =>
          user should be(users.headOption)

          fixtures.credentialsManager.resourceOwnersCreated should be(0)
          fixtures.credentialsManager.resourceOwnersActivated should be(0)
          fixtures.credentialsManager.resourceOwnersDeactivated should be(1)
          fixtures.credentialsManager.resourceOwnerPasswordsSet should be(0)
          fixtures.credentialsManager.notFound should be(0)
          fixtures.credentialsManager.conflicts should be(1)
          fixtures.credentialsManager.failures should be(0)
        }
    }
  }

  they should "fail to update limits if a user is missing" in withRetry {
    val fixtures = new TestFixtures {}
    Put(s"/${User.generateId()}/limits")
      .withEntity(updateLimitsRequest) ~> fixtures.routes ~> check {
      status should be(StatusCodes.BadRequest)
    }
  }

  they should "fail to update permissions if a user is missing" in withRetry {
    val fixtures = new TestFixtures {}
    Put(s"/${User.generateId()}/permissions")
      .withEntity(updatePermissionsRequest) ~> fixtures.routes ~> check {
      status should be(StatusCodes.BadRequest)
    }
  }

  they should "fail to update state if a user is missing" in withRetry {
    val fixtures = new TestFixtures {}

    val updateStateRequest = UpdateUserState(
      active = false
    )

    Put(s"/${User.generateId()}/state")
      .withEntity(updateStateRequest) ~> fixtures.routes ~> check {
      status should be(StatusCodes.BadRequest)
    }
  }

  they should "delete existing users" in withRetry {
    val fixtures = new TestFixtures {}
    fixtures.userStore.manage().create(users.head).await

    Delete(s"/${users.head.id}") ~> fixtures.routes ~> check {
      status should be(StatusCodes.OK)
      responseAs[DeletedUser] should be(DeletedUser(existing = true))

      fixtures.userStore
        .view()
        .get(users.head.id)
        .map { user =>
          user should be(None)

          fixtures.credentialsManager.resourceOwnersCreated should be(0)
          fixtures.credentialsManager.resourceOwnersActivated should be(0)
          fixtures.credentialsManager.resourceOwnersDeactivated should be(1)
          fixtures.credentialsManager.resourceOwnerPasswordsSet should be(0)
          fixtures.credentialsManager.notFound should be(0)
          fixtures.credentialsManager.conflicts should be(0)
          fixtures.credentialsManager.failures should be(0)
        }
    }
  }

  they should "not delete missing users" in withRetry {
    val fixtures = new TestFixtures {}

    Delete(s"/${users.head.id}") ~> fixtures.routes ~> check {
      status should be(StatusCodes.OK)
      responseAs[DeletedUser] should be(DeletedUser(existing = false))
    }
  }

  they should "handle resource owner deactivation failures when deleting users (not found)" in withRetry {
    val fixtures = new TestFixtures {
      override lazy val credentialsManager: MockUserCredentialsManager =
        MockUserCredentialsManager(
          withResult = Success(UserCredentialsManager.Result.NotFound("test-message"))
        )
    }
    fixtures.userStore.manage().create(users.head).await

    Delete(s"/${users.head.id}") ~> fixtures.routes ~> check {
      status should be(StatusCodes.NotFound)

      fixtures.userStore
        .view()
        .get(users.head.id)
        .map { user =>
          user should be(users.headOption)

          fixtures.credentialsManager.resourceOwnersCreated should be(0)
          fixtures.credentialsManager.resourceOwnersActivated should be(0)
          fixtures.credentialsManager.resourceOwnersDeactivated should be(1)
          fixtures.credentialsManager.resourceOwnerPasswordsSet should be(0)
          fixtures.credentialsManager.notFound should be(1)
          fixtures.credentialsManager.conflicts should be(0)
          fixtures.credentialsManager.failures should be(0)
        }
    }
  }

  they should "handle resource owner deactivation failures when deleting users (conflict)" in withRetry {
    val fixtures = new TestFixtures {
      override lazy val credentialsManager: MockUserCredentialsManager =
        MockUserCredentialsManager(
          withResult = Success(UserCredentialsManager.Result.Conflict("test-message"))
        )
    }
    fixtures.userStore.manage().create(users.head).await

    Delete(s"/${users.head.id}") ~> fixtures.routes ~> check {
      status should be(StatusCodes.Conflict)

      fixtures.userStore
        .view()
        .get(users.head.id)
        .map { user =>
          user should be(users.headOption)

          fixtures.credentialsManager.resourceOwnersCreated should be(0)
          fixtures.credentialsManager.resourceOwnersActivated should be(0)
          fixtures.credentialsManager.resourceOwnersDeactivated should be(1)
          fixtures.credentialsManager.resourceOwnerPasswordsSet should be(0)
          fixtures.credentialsManager.notFound should be(0)
          fixtures.credentialsManager.conflicts should be(1)
          fixtures.credentialsManager.failures should be(0)
        }
    }
  }

  they should "update existing users' passwords (with authentication password hashing)" in withRetry {
    val fixtures = new TestFixtures {}
    fixtures.userStore.manage().create(users.head).await

    Put(s"/${users.head.id}/password")
      .withEntity(resetUserPasswordRequest) ~> fixtures.routes ~> check {
      status should be(StatusCodes.OK)

      fixtures.credentialsManager.resourceOwnersCreated should be(0)
      fixtures.credentialsManager.resourceOwnersActivated should be(0)
      fixtures.credentialsManager.resourceOwnersDeactivated should be(0)
      fixtures.credentialsManager.resourceOwnerPasswordsSet should be(1)
      fixtures.credentialsManager.notFound should be(0)
      fixtures.credentialsManager.conflicts should be(0)
      fixtures.credentialsManager.failures should be(0)

      fixtures.credentialsManager.latestPassword should not be empty
      fixtures.credentialsManager.latestPassword should not be resetUserPasswordRequest.rawPassword
    }
  }

  they should "update existing users' passwords (without authentication password hashing)" in withRetry {
    val fixtures = new TestFixtures {
      override lazy val hashAuthenticationPasswords: Boolean = false
    }

    fixtures.userStore.manage().create(users.head).await

    Put(s"/${users.head.id}/password")
      .withEntity(resetUserPasswordRequest) ~> fixtures.routes ~> check {
      status should be(StatusCodes.OK)

      fixtures.credentialsManager.resourceOwnersCreated should be(0)
      fixtures.credentialsManager.resourceOwnersActivated should be(0)
      fixtures.credentialsManager.resourceOwnersDeactivated should be(0)
      fixtures.credentialsManager.resourceOwnerPasswordsSet should be(1)
      fixtures.credentialsManager.notFound should be(0)
      fixtures.credentialsManager.conflicts should be(0)
      fixtures.credentialsManager.failures should be(0)

      fixtures.credentialsManager.latestPassword should not be empty
      fixtures.credentialsManager.latestPassword should be(resetUserPasswordRequest.rawPassword)
    }
  }

  they should "handle user password update failures (not found)" in withRetry {
    val fixtures = new TestFixtures {
      override lazy val credentialsManager: MockUserCredentialsManager =
        MockUserCredentialsManager(
          withResult = Success(UserCredentialsManager.Result.NotFound("test-message"))
        )
    }

    fixtures.userStore.manage().create(users.head).await

    Put(s"/${users.head.id}/password")
      .withEntity(resetUserPasswordRequest) ~> fixtures.routes ~> check {
      status should be(StatusCodes.NotFound)

      fixtures.credentialsManager.resourceOwnersCreated should be(0)
      fixtures.credentialsManager.resourceOwnersActivated should be(0)
      fixtures.credentialsManager.resourceOwnersDeactivated should be(0)
      fixtures.credentialsManager.resourceOwnerPasswordsSet should be(1)
      fixtures.credentialsManager.notFound should be(1)
      fixtures.credentialsManager.conflicts should be(0)
      fixtures.credentialsManager.failures should be(0)
    }
  }

  they should "handle user password update failures (conflict)" in withRetry {
    val fixtures = new TestFixtures {
      override lazy val credentialsManager: MockUserCredentialsManager =
        MockUserCredentialsManager(
          withResult = Success(UserCredentialsManager.Result.Conflict("test-message"))
        )
    }

    fixtures.userStore.manage().create(users.head).await

    Put(s"/${users.head.id}/password")
      .withEntity(resetUserPasswordRequest) ~> fixtures.routes ~> check {
      status should be(StatusCodes.Conflict)

      fixtures.credentialsManager.resourceOwnersCreated should be(0)
      fixtures.credentialsManager.resourceOwnersActivated should be(0)
      fixtures.credentialsManager.resourceOwnersDeactivated should be(0)
      fixtures.credentialsManager.resourceOwnerPasswordsSet should be(1)
      fixtures.credentialsManager.notFound should be(0)
      fixtures.credentialsManager.conflicts should be(1)
      fixtures.credentialsManager.failures should be(0)
    }
  }

  "Users routes (self permissions)" should "respond with the current user" in withRetry {
    val fixtures = new TestFixtures {}
    fixtures.userStore.manage().create(users.head).await

    Get("/self") ~> fixtures.routes ~> check {
      status should be(StatusCodes.OK)
      responseAs[User] should be(users.head)
    }
  }

  they should "fail if the current user is missing" in withRetry {
    val fixtures = new TestFixtures {}
    Get("/self") ~> fixtures.routes ~> check {
      status should be(StatusCodes.NotFound)
    }
  }

  they should "deactivate the current user" in withRetry {
    val fixtures = new TestFixtures {}
    fixtures.userStore.manage().create(users.head).await

    Put("/self/deactivate") ~> fixtures.routes ~> check {
      status should be(StatusCodes.OK)

      fixtures.userStore
        .view()
        .get(users.head.id)
        .map { user =>
          user.map(_.active) should be(Some(false))

          fixtures.credentialsManager.resourceOwnersCreated should be(0)
          fixtures.credentialsManager.resourceOwnersActivated should be(0)
          fixtures.credentialsManager.resourceOwnersDeactivated should be(1)
          fixtures.credentialsManager.resourceOwnerPasswordsSet should be(0)
          fixtures.credentialsManager.notFound should be(0)
          fixtures.credentialsManager.conflicts should be(0)
          fixtures.credentialsManager.failures should be(0)
        }
    }
  }

  they should "handle resource owner deactivation failures (not found)" in withRetry {
    val fixtures = new TestFixtures {
      override lazy val credentialsManager: MockUserCredentialsManager =
        MockUserCredentialsManager(
          withResult = Success(UserCredentialsManager.Result.NotFound("test-message"))
        )
    }
    fixtures.userStore.manage().create(users.head).await

    Put("/self/deactivate") ~> fixtures.routes ~> check {
      status should be(StatusCodes.NotFound)

      fixtures.userStore
        .view()
        .get(users.head.id)
        .map { user =>
          user should be(users.headOption)

          fixtures.credentialsManager.resourceOwnersCreated should be(0)
          fixtures.credentialsManager.resourceOwnersActivated should be(0)
          fixtures.credentialsManager.resourceOwnersDeactivated should be(1)
          fixtures.credentialsManager.resourceOwnerPasswordsSet should be(0)
          fixtures.credentialsManager.notFound should be(1)
          fixtures.credentialsManager.conflicts should be(0)
          fixtures.credentialsManager.failures should be(0)
        }
    }
  }

  they should "handle resource owner deactivation failures (conflict)" in withRetry {
    val fixtures = new TestFixtures {
      override lazy val credentialsManager: MockUserCredentialsManager =
        MockUserCredentialsManager(
          withResult = Success(UserCredentialsManager.Result.Conflict("test-message"))
        )
    }
    fixtures.userStore.manage().create(users.head).await

    Put("/self/deactivate") ~> fixtures.routes ~> check {
      status should be(StatusCodes.Conflict)

      fixtures.userStore
        .view()
        .get(users.head.id)
        .map { user =>
          user should be(users.headOption)

          fixtures.credentialsManager.resourceOwnersCreated should be(0)
          fixtures.credentialsManager.resourceOwnersActivated should be(0)
          fixtures.credentialsManager.resourceOwnersDeactivated should be(1)
          fixtures.credentialsManager.resourceOwnerPasswordsSet should be(0)
          fixtures.credentialsManager.notFound should be(0)
          fixtures.credentialsManager.conflicts should be(1)
          fixtures.credentialsManager.failures should be(0)
        }
    }
  }

  they should "reset the current user's password salt" in withRetry {
    val fixtures = new TestFixtures {}
    fixtures.userStore.manage().create(users.head).await

    Put("/self/salt") ~> fixtures.routes ~> check {
      status should be(StatusCodes.OK)

      val updatedSalt = entityAs[UpdatedUserSalt]

      fixtures.userStore
        .view()
        .get(users.head.id)
        .map { user =>
          updatedSalt.salt should be(user.map(_.salt).getOrElse("invalid"))
          updatedSalt.salt should not be users.headOption.map(_.salt).getOrElse("invalid")
          user should be(users.headOption.map(_.copy(salt = updatedSalt.salt)))

          fixtures.credentialsManager.resourceOwnersCreated should be(0)
          fixtures.credentialsManager.resourceOwnersActivated should be(0)
          fixtures.credentialsManager.resourceOwnersDeactivated should be(0)
          fixtures.credentialsManager.resourceOwnerPasswordsSet should be(0)
          fixtures.credentialsManager.notFound should be(0)
          fixtures.credentialsManager.conflicts should be(0)
          fixtures.credentialsManager.failures should be(0)
        }
    }
  }

  they should "fail to reset the current user's password salt if the current user is missing" in withRetry {
    val fixtures = new TestFixtures {}
    Put("/self/salt") ~> fixtures.routes ~> check {
      status should be(StatusCodes.NotFound)
    }
  }

  they should "update the current user's password" in withRetry {
    val fixtures = new TestFixtures {}
    fixtures.userStore.manage().create(users.head).await

    Put("/self/password").withEntity(resetUserPasswordRequest) ~> fixtures.routes ~> check {
      status should be(StatusCodes.OK)

      fixtures.credentialsManager.resourceOwnersCreated should be(0)
      fixtures.credentialsManager.resourceOwnersActivated should be(0)
      fixtures.credentialsManager.resourceOwnersDeactivated should be(0)
      fixtures.credentialsManager.resourceOwnerPasswordsSet should be(1)
      fixtures.credentialsManager.notFound should be(0)
      fixtures.credentialsManager.conflicts should be(0)
      fixtures.credentialsManager.failures should be(0)

      fixtures.credentialsManager.latestPassword should not be empty

      // when users reset their own password, hashing happens on the client side
      fixtures.credentialsManager.latestPassword should be(resetUserPasswordRequest.rawPassword)
    }
  }

  they should "handle current user password update failures (not found)" in withRetry {
    val fixtures = new TestFixtures {
      override lazy val credentialsManager: MockUserCredentialsManager =
        MockUserCredentialsManager(
          withResult = Success(UserCredentialsManager.Result.NotFound("test-message"))
        )
    }

    fixtures.userStore.manage().create(users.head).await

    Put("/self/password").withEntity(resetUserPasswordRequest) ~> fixtures.routes ~> check {
      status should be(StatusCodes.NotFound)

      fixtures.credentialsManager.resourceOwnersCreated should be(0)
      fixtures.credentialsManager.resourceOwnersActivated should be(0)
      fixtures.credentialsManager.resourceOwnersDeactivated should be(0)
      fixtures.credentialsManager.resourceOwnerPasswordsSet should be(1)
      fixtures.credentialsManager.notFound should be(1)
      fixtures.credentialsManager.conflicts should be(0)
      fixtures.credentialsManager.failures should be(0)
    }
  }

  they should "handle current user password update failures (conflict)" in withRetry {
    val fixtures = new TestFixtures {
      override lazy val credentialsManager: MockUserCredentialsManager =
        MockUserCredentialsManager(
          withResult = Success(UserCredentialsManager.Result.Conflict("test-message"))
        )
    }

    fixtures.userStore.manage().create(users.head).await

    Put("/self/password").withEntity(resetUserPasswordRequest) ~> fixtures.routes ~> check {
      status should be(StatusCodes.Conflict)

      fixtures.credentialsManager.resourceOwnersCreated should be(0)
      fixtures.credentialsManager.resourceOwnersActivated should be(0)
      fixtures.credentialsManager.resourceOwnersDeactivated should be(0)
      fixtures.credentialsManager.resourceOwnerPasswordsSet should be(1)
      fixtures.credentialsManager.notFound should be(0)
      fixtures.credentialsManager.conflicts should be(1)
      fixtures.credentialsManager.failures should be(0)
    }
  }

  they should "fail to update the current user's password if the user is missing" in withRetry {
    val fixtures = new TestFixtures {}

    Put("/self/password").withEntity(resetUserPasswordRequest) ~> fixtures.routes ~> check {
      status should be(StatusCodes.NotFound)

      fixtures.credentialsManager.resourceOwnersCreated should be(0)
      fixtures.credentialsManager.resourceOwnersActivated should be(0)
      fixtures.credentialsManager.resourceOwnersDeactivated should be(0)
      fixtures.credentialsManager.resourceOwnerPasswordsSet should be(0)
      fixtures.credentialsManager.notFound should be(0)
      fixtures.credentialsManager.conflicts should be(0)
      fixtures.credentialsManager.failures should be(0)
    }
  }

  private implicit val typedSystem: ActorSystem[Nothing] = ActorSystem(
    Behaviors.ignore,
    "UsersSpec"
  )

  private implicit val untypedSystem: org.apache.pekko.actor.ActorSystem = typedSystem.classicSystem

  private implicit val log: Logger = LoggerFactory.getLogger(this.getClass.getName)

  private implicit val telemetry: TelemetryContext = MockTelemetryContext()

  private trait TestFixtures {
    lazy val userStore: UserStore = MockUserStore()
    lazy val credentialsManager: MockUserCredentialsManager = MockUserCredentialsManager()
    lazy val hashAuthenticationPasswords: Boolean = true

    implicit lazy val provider: ResourceProvider = new MockResourceProvider(
      resources = Set(
        userStore.view(),
        userStore.viewSelf(),
        userStore.manage(),
        userStore.manageSelf()
      )
    )

    lazy implicit val context: RoutesContext = RoutesContext.collect()

    lazy val routes: Route = new Users(
      credentialsManager = credentialsManager,
      secretsConfig = testSecretsConfig.copy(
        derivation = testSecretsConfig.derivation.copy(
          authentication = testSecretsConfig.derivation.authentication.copy(enabled = hashAuthenticationPasswords)
        )
      )
    ).routes
  }

  private implicit val user: CurrentUser = CurrentUser(User.generateId())

  private val users = Seq(
    User(
      id = user.id,
      salt = "test-salt-01",
      active = true,
      limits = None,
      permissions = Set.empty
    ),
    User(
      id = User.generateId(),
      salt = "test-salt-02",
      active = true,
      limits = None,
      permissions = Set.empty
    )
  )

  private val createRequest = CreateUser(
    username = "test-user",
    rawPassword = "test-password",
    limits = None,
    permissions = Set.empty
  )

  private val updateLimitsRequest = UpdateUserLimits(
    limits = Some(
      User.Limits(
        maxDevices = 1,
        maxCrates = 2,
        maxStorage = 3,
        maxStoragePerCrate = 4,
        maxRetention = 5.hours,
        minRetention = 6.minutes
      )
    )
  )

  private val updatePermissionsRequest = UpdateUserPermissions(
    permissions = Set(Permission.View.Service)
  )

  private val resetUserPasswordRequest = ResetUserPassword(
    rawPassword = "test-password"
  )

  import scala.language.implicitConversions

  private implicit def createRequestToEntity(request: CreateUser): RequestEntity =
    Marshal(request).to[RequestEntity].await

  private implicit def updateRequestToEntity(request: UpdateUserLimits): RequestEntity =
    Marshal(request).to[RequestEntity].await

  private implicit def updateRequestToEntity(request: UpdateUserPermissions): RequestEntity =
    Marshal(request).to[RequestEntity].await

  private implicit def updateRequestToEntity(request: UpdateUserState): RequestEntity =
    Marshal(request).to[RequestEntity].await

  private implicit def resetUserPasswordRequestToEntity(request: ResetUserPassword): RequestEntity =
    Marshal(request).to[RequestEntity].await
}
