package stasis.test.specs.unit.server.api.routes

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorSystem, Behavior, SpawnProtocol}
import akka.http.scaladsl.marshalling.Marshal
import akka.http.scaladsl.model.{RequestEntity, StatusCodes}
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.testkit.ScalatestRouteTest
import org.slf4j.{Logger, LoggerFactory}
import stasis.server.api.routes.{RoutesContext, Users}
import stasis.server.model.users.UserStore
import stasis.server.security.{CurrentUser, ResourceProvider}
import stasis.shared.api.requests.{CreateUser, UpdateUserLimits, UpdateUserPermissions, UpdateUserState}
import stasis.shared.api.responses.{CreatedUser, DeletedUser}
import stasis.shared.model.users.User
import stasis.shared.security.Permission
import stasis.test.specs.unit.AsyncUnitSpec
import stasis.test.specs.unit.server.model.mocks.MockUserStore
import stasis.test.specs.unit.server.security.mocks.MockResourceProvider

import scala.concurrent.Future
import scala.concurrent.duration._

class UsersSpec extends AsyncUnitSpec with ScalatestRouteTest {
  import de.heikoseeberger.akkahttpplayjson.PlayJsonSupport._
  import stasis.shared.api.Formats._

  "Users routes (full permissions)" should "respond with all users" in {
    val fixtures = new TestFixtures {}
    Future.sequence(users.map(fixtures.userStore.manage().create)).await

    Get("/") ~> fixtures.routes ~> check {
      status should be(StatusCodes.OK)
      responseAs[Seq[User]] should contain theSameElementsAs users
    }
  }

  they should "create new users" in {
    val fixtures = new TestFixtures {}
    Post("/").withEntity(createRequest) ~> fixtures.routes ~> check {
      status should be(StatusCodes.OK)

      fixtures.userStore
        .view()
        .get(entityAs[CreatedUser].user)
        .map(_.isDefined should be(true))
    }
  }

  they should "respond with existing users" in {
    val fixtures = new TestFixtures {}

    fixtures.userStore.manage().create(users.head).await

    Get(s"/${users.head.id}") ~> fixtures.routes ~> check {
      status should be(StatusCodes.OK)
      responseAs[User] should be(users.head)
    }
  }

  they should "fail if a user is missing" in {
    val fixtures = new TestFixtures {}
    Get(s"/${User.generateId()}") ~> fixtures.routes ~> check {
      status should be(StatusCodes.NotFound)
    }
  }

  they should "update existing users' limits" in {
    val fixtures = new TestFixtures {}
    fixtures.userStore.manage().create(users.head).await

    Put(s"/${users.head.id}/limits")
      .withEntity(updateLimitsRequest) ~> fixtures.routes ~> check {
      fixtures.userStore
        .view()
        .get(users.head.id)
        .map(_.map(_.limits) should be(Some(updateLimitsRequest.limits)))
    }
  }

  they should "update existing users' permissions" in {
    val fixtures = new TestFixtures {}
    fixtures.userStore.manage().create(users.head).await

    Put(s"/${users.head.id}/permissions")
      .withEntity(updatePermissionsRequest) ~> fixtures.routes ~> check {
      fixtures.userStore
        .view()
        .get(users.head.id)
        .map(_.map(_.permissions.size).getOrElse(0) should be(updatePermissionsRequest.permissions.size))
    }
  }

  they should "update existing users' state" in {
    val fixtures = new TestFixtures {}
    fixtures.userStore.manage().create(users.head).await

    Put(s"/${users.head.id}/state")
      .withEntity(updateStateRequest) ~> fixtures.routes ~> check {
      fixtures.userStore
        .view()
        .get(users.head.id)
        .map(_.map(_.active) should be(Some(updateStateRequest.active)))
    }
  }

  they should "fail to update limits if a user is missing" in {
    val fixtures = new TestFixtures {}
    Put(s"/${User.generateId()}/limits")
      .withEntity(updateLimitsRequest) ~> fixtures.routes ~> check {
      status should be(StatusCodes.BadRequest)
    }
  }

  they should "fail to update permissions if a user is missing" in {
    val fixtures = new TestFixtures {}
    Put(s"/${User.generateId()}/permissions")
      .withEntity(updatePermissionsRequest) ~> fixtures.routes ~> check {
      status should be(StatusCodes.BadRequest)
    }
  }

  they should "fail to update state if a user is missing" in {
    val fixtures = new TestFixtures {}
    Put(s"/${User.generateId()}/state")
      .withEntity(updateStateRequest) ~> fixtures.routes ~> check {
      status should be(StatusCodes.BadRequest)
    }
  }

  they should "delete existing users" in {
    val fixtures = new TestFixtures {}
    fixtures.userStore.manage().create(users.head).await

    Delete(s"/${users.head.id}") ~> fixtures.routes ~> check {
      status should be(StatusCodes.OK)
      responseAs[DeletedUser] should be(DeletedUser(existing = true))

      fixtures.userStore
        .view()
        .get(users.head.id)
        .map(_ should be(None))
    }
  }

  they should "not delete missing users" in {
    val fixtures = new TestFixtures {}

    Delete(s"/${users.head.id}") ~> fixtures.routes ~> check {
      status should be(StatusCodes.OK)
      responseAs[DeletedUser] should be(DeletedUser(existing = false))
    }
  }

  "Users routes (self permissions)" should "respond with the current user" in {
    val fixtures = new TestFixtures {}
    fixtures.userStore.manage().create(users.head).await

    Get("/self") ~> fixtures.routes ~> check {
      status should be(StatusCodes.OK)
      responseAs[User] should be(users.head)
    }
  }

  they should "fail if the current user is missing" in {
    val fixtures = new TestFixtures {}
    Get("/self") ~> fixtures.routes ~> check {
      status should be(StatusCodes.NotFound)
    }
  }

  they should "deactivate the current user" in {
    val fixtures = new TestFixtures {}
    fixtures.userStore.manage().create(users.head).await

    Put("/self/deactivate") ~> fixtures.routes ~> check {
      status should be(StatusCodes.OK)

      fixtures.userStore
        .view()
        .get(users.head.id)
        .map(_.map(_.active) should be(Some(false)))
    }
  }

  private implicit val typedSystem: ActorSystem[SpawnProtocol.Command] = ActorSystem(
    Behaviors.setup(_ => SpawnProtocol()): Behavior[SpawnProtocol.Command],
    "UsersSpec"
  )

  private implicit val untypedSystem: akka.actor.ActorSystem = typedSystem.classicSystem

  private implicit val log: Logger = LoggerFactory.getLogger(this.getClass.getName)

  private trait TestFixtures {
    lazy val userStore: UserStore = MockUserStore()

    implicit lazy val provider: ResourceProvider = new MockResourceProvider(
      resources = Set(
        userStore.view(),
        userStore.viewSelf(),
        userStore.manage(),
        userStore.manageSelf()
      )
    )

    lazy implicit val context: RoutesContext = RoutesContext.collect()

    lazy val routes: Route = new Users().routes
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

  private val updateStateRequest = UpdateUserState(
    active = false
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
}
