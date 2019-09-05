package stasis.test.specs.unit.server.api.routes

import java.time.LocalTime

import scala.concurrent.Future
import scala.concurrent.duration._

import akka.actor.ActorSystem
import akka.event.{Logging, LoggingAdapter}
import akka.http.scaladsl.marshalling.Marshal
import akka.http.scaladsl.model.{RequestEntity, StatusCodes}
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.testkit.ScalatestRouteTest
import stasis.server.api.routes.{RoutesContext, Schedules}
import stasis.server.model.schedules.ScheduleStore
import stasis.server.security.{CurrentUser, ResourceProvider}
import stasis.shared.api.requests.{CreateSchedule, UpdateSchedule}
import stasis.shared.api.responses.{CreatedSchedule, DeletedSchedule}
import stasis.shared.model.schedules.Schedule
import stasis.shared.model.users.User
import stasis.test.specs.unit.AsyncUnitSpec
import stasis.test.specs.unit.server.model.mocks.MockScheduleStore
import stasis.test.specs.unit.server.security.mocks.MockResourceProvider

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

  private implicit val untypedSystem: ActorSystem = ActorSystem(name = "SchedulesSpec")
  private implicit val log: LoggingAdapter = Logging(untypedSystem, this.getClass.getName)

  private trait TestFixtures {
    lazy val scheduleStore: ScheduleStore = MockScheduleStore()

    lazy implicit val provider: ResourceProvider = new MockResourceProvider(
      resources = Set(
        scheduleStore.view(),
        scheduleStore.manage()
      )
    )

    lazy implicit val context: RoutesContext = RoutesContext.collect()

    lazy val routes: Route = Schedules()
  }

  private implicit val user: CurrentUser = CurrentUser(User.generateId())

  private val schedules = Seq(
    Schedule(
      id = Schedule.generateId(),
      process = Schedule.Process.Backup,
      instant = LocalTime.now(),
      interval = 3.seconds,
      missed = Schedule.MissedAction.ExecuteImmediately,
      overlap = Schedule.OverlapAction.ExecuteAnyway
    ),
    Schedule(
      id = Schedule.generateId(),
      process = Schedule.Process.Expiration,
      instant = LocalTime.now(),
      interval = 3.hours,
      missed = Schedule.MissedAction.ExecuteNext,
      overlap = Schedule.OverlapAction.CancelNew
    )
  )

  private val createRequest = CreateSchedule(
    process = Schedule.Process.Backup,
    instant = LocalTime.now(),
    interval = 3.seconds,
    missed = Schedule.MissedAction.ExecuteImmediately,
    overlap = Schedule.OverlapAction.ExecuteAnyway
  )

  private val updateRequest = UpdateSchedule(
    process = Schedule.Process.Backup,
    instant = LocalTime.now(),
    interval = 5.hours,
    missed = Schedule.MissedAction.ExecuteImmediately,
    overlap = Schedule.OverlapAction.ExecuteAnyway
  )

  import scala.language.implicitConversions

  private implicit def createRequestToEntity(request: CreateSchedule): RequestEntity =
    Marshal(request).to[RequestEntity].await

  private implicit def updateRequestToEntity(request: UpdateSchedule): RequestEntity =
    Marshal(request).to[RequestEntity].await
}
