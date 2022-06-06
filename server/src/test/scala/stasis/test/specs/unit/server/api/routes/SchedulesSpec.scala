package stasis.test.specs.unit.server.api.routes

import java.time.LocalDateTime
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorSystem, Behavior, SpawnProtocol}
import akka.http.scaladsl.marshalling.Marshal
import akka.http.scaladsl.model.{RequestEntity, StatusCodes}
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.testkit.ScalatestRouteTest
import org.slf4j.{Logger, LoggerFactory}
import stasis.core.telemetry.TelemetryContext
import stasis.server.api.routes.{RoutesContext, Schedules}
import stasis.server.model.schedules.ScheduleStore
import stasis.server.security.{CurrentUser, ResourceProvider}
import stasis.shared.api.requests.{CreateSchedule, UpdateSchedule}
import stasis.shared.api.responses.{CreatedSchedule, DeletedSchedule}
import stasis.shared.model.schedules.Schedule
import stasis.shared.model.users.User
import stasis.test.specs.unit.AsyncUnitSpec
import stasis.test.specs.unit.core.telemetry.MockTelemetryContext
import stasis.test.specs.unit.server.model.mocks.MockScheduleStore
import stasis.test.specs.unit.server.security.mocks.MockResourceProvider

import scala.concurrent.Future
import scala.concurrent.duration._

class SchedulesSpec extends AsyncUnitSpec with ScalatestRouteTest {
  import de.heikoseeberger.akkahttpplayjson.PlayJsonSupport._
  import stasis.shared.api.Formats._

  "Schedules routes" should "respond with all schedules" in {
    val fixtures = new TestFixtures {}
    Future.sequence(schedules.map(fixtures.scheduleStore.manage().create)).await

    Get("/") ~> fixtures.routes ~> check {
      status should be(StatusCodes.OK)
      responseAs[Seq[Schedule]] should contain theSameElementsAs schedules
    }
  }

  they should "create new schedules" in {
    val fixtures = new TestFixtures {}
    Post("/").withEntity(createRequest) ~> fixtures.routes ~> check {
      status should be(StatusCodes.OK)

      fixtures.scheduleStore
        .view()
        .get(entityAs[CreatedSchedule].schedule)
        .map(_.isDefined should be(true))
    }
  }

  they should "respond with all public schedules" in {
    val fixtures = new TestFixtures {}
    Future.sequence(schedules.map(fixtures.scheduleStore.manage().create)).await

    Get("/public") ~> fixtures.routes ~> check {
      status should be(StatusCodes.OK)
      responseAs[List[Schedule]] match {
        case publicSchedule :: Nil => publicSchedule should be(schedules.head)
        case other                 => fail(s"Unexpected response received: [$other]")
      }
    }
  }

  they should "respond with existing public schedules" in {
    val fixtures = new TestFixtures {}

    fixtures.scheduleStore.manage().create(schedules.head).await

    Get(s"/public/${schedules.head.id}") ~> fixtures.routes ~> check {
      status should be(StatusCodes.OK)
      responseAs[Schedule] should be(schedules.head)
    }
  }

  they should "fail if a public schedule is missing" in {
    val fixtures = new TestFixtures {}
    Get(s"/public/${Schedule.generateId()}") ~> fixtures.routes ~> check {
      status should be(StatusCodes.NotFound)
    }
  }

  they should "respond with existing schedules" in {
    val fixtures = new TestFixtures {}

    fixtures.scheduleStore.manage().create(schedules.head).await

    Get(s"/${schedules.head.id}") ~> fixtures.routes ~> check {
      status should be(StatusCodes.OK)
      responseAs[Schedule] should be(schedules.head)
    }
  }

  they should "fail if a schedule is missing" in {
    val fixtures = new TestFixtures {}
    Get(s"/${Schedule.generateId()}") ~> fixtures.routes ~> check {
      status should be(StatusCodes.NotFound)
    }
  }

  they should "update existing schedule" in {
    val fixtures = new TestFixtures {}
    fixtures.scheduleStore.manage().create(schedules.head).await

    Put(s"/${schedules.head.id}")
      .withEntity(updateRequest) ~> fixtures.routes ~> check {
      status should be(StatusCodes.OK)

      fixtures.scheduleStore
        .view()
        .get(schedules.head.id)
        .map(_.map(_.interval) should be(Some(updateRequest.interval)))
    }
  }

  they should "fail to update if a schedule is missing" in {
    val fixtures = new TestFixtures {}
    Put(s"/${Schedule.generateId()}")
      .withEntity(updateRequest) ~> fixtures.routes ~> check {
      status should be(StatusCodes.BadRequest)
    }
  }

  they should "delete existing schedules" in {
    val fixtures = new TestFixtures {}
    fixtures.scheduleStore.manage().create(schedules.head).await

    Delete(s"/${schedules.head.id}") ~> fixtures.routes ~> check {
      status should be(StatusCodes.OK)
      responseAs[DeletedSchedule] should be(DeletedSchedule(existing = true))

      fixtures.scheduleStore
        .view()
        .get(schedules.head.id)
        .map(_ should be(None))
    }
  }

  they should "not delete missing schedules" in {
    val fixtures = new TestFixtures {}

    Delete(s"/${schedules.head.id}") ~> fixtures.routes ~> check {
      status should be(StatusCodes.OK)
      responseAs[DeletedSchedule] should be(DeletedSchedule(existing = false))
    }
  }

  private implicit val typedSystem: ActorSystem[SpawnProtocol.Command] = ActorSystem(
    Behaviors.setup(_ => SpawnProtocol()): Behavior[SpawnProtocol.Command],
    "SchedulesSpec"
  )

  private implicit val untypedSystem: akka.actor.ActorSystem = typedSystem.classicSystem

  private implicit val log: Logger = LoggerFactory.getLogger(this.getClass.getName)

  private implicit val telemetry: TelemetryContext = MockTelemetryContext()

  private trait TestFixtures {
    lazy val scheduleStore: ScheduleStore = MockScheduleStore()

    lazy implicit val provider: ResourceProvider = new MockResourceProvider(
      resources = Set(
        scheduleStore.view(),
        scheduleStore.viewPublic(),
        scheduleStore.manage()
      )
    )

    lazy implicit val context: RoutesContext = RoutesContext.collect()

    lazy val routes: Route = new Schedules().routes
  }

  private implicit val user: CurrentUser = CurrentUser(User.generateId())

  private val schedules = Seq(
    Schedule(
      id = Schedule.generateId(),
      info = "test-schedule-01",
      isPublic = true,
      start = LocalDateTime.now(),
      interval = 3.seconds
    ),
    Schedule(
      id = Schedule.generateId(),
      info = "test-schedule-02",
      isPublic = false,
      start = LocalDateTime.now(),
      interval = 3.hours
    )
  )

  private val createRequest = CreateSchedule(
    isPublic = true,
    info = "test-schedule",
    start = LocalDateTime.now(),
    interval = 3.seconds
  )

  private val updateRequest = UpdateSchedule(
    info = "updated-test-schedule",
    start = LocalDateTime.now(),
    interval = 5.hours
  )

  import scala.language.implicitConversions

  private implicit def createRequestToEntity(request: CreateSchedule): RequestEntity =
    Marshal(request).to[RequestEntity].await

  private implicit def updateRequestToEntity(request: UpdateSchedule): RequestEntity =
    Marshal(request).to[RequestEntity].await
}
