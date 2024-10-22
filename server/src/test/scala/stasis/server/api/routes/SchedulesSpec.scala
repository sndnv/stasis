package stasis.server.api.routes

import java.time.Instant
import java.time.LocalDateTime

import scala.concurrent.Future
import scala.concurrent.duration._

import org.apache.pekko.actor.typed.ActorSystem
import org.apache.pekko.actor.typed.scaladsl.Behaviors
import org.apache.pekko.http.scaladsl.marshalling.Marshal
import org.apache.pekko.http.scaladsl.model.RequestEntity
import org.apache.pekko.http.scaladsl.model.StatusCodes
import org.apache.pekko.http.scaladsl.server.Route
import org.apache.pekko.http.scaladsl.testkit.ScalatestRouteTest
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import stasis.server.persistence.schedules.MockScheduleStore
import stasis.server.persistence.schedules.ScheduleStore
import stasis.server.security.CurrentUser
import stasis.server.security.ResourceProvider
import stasis.server.security.mocks.MockResourceProvider
import stasis.shared.api.requests.CreateSchedule
import stasis.shared.api.requests.UpdateSchedule
import stasis.shared.api.responses.CreatedSchedule
import stasis.shared.api.responses.DeletedSchedule
import stasis.shared.model.schedules.Schedule
import stasis.shared.model.users.User
import stasis.test.specs.unit.AsyncUnitSpec

class SchedulesSpec extends AsyncUnitSpec with ScalatestRouteTest {
  import com.github.pjfanning.pekkohttpplayjson.PlayJsonSupport._

  import stasis.shared.api.Formats._

  "Schedules routes" should "respond with all schedules" in withRetry {
    val fixtures = new TestFixtures {}
    Future.sequence(schedules.map(fixtures.scheduleStore.manage().put)).await

    Get("/") ~> fixtures.routes ~> check {
      status should be(StatusCodes.OK)
      responseAs[Seq[Schedule]] should contain theSameElementsAs schedules
    }
  }

  they should "create new schedules" in withRetry {
    val fixtures = new TestFixtures {}
    Post("/").withEntity(createRequest) ~> fixtures.routes ~> check {
      status should be(StatusCodes.OK)

      fixtures.scheduleStore
        .view()
        .get(entityAs[CreatedSchedule].schedule)
        .map(_.isDefined should be(true))
    }
  }

  they should "respond with all public schedules" in withRetry {
    val fixtures = new TestFixtures {}
    Future.sequence(schedules.map(fixtures.scheduleStore.manage().put)).await

    Get("/public") ~> fixtures.routes ~> check {
      status should be(StatusCodes.OK)
      responseAs[List[Schedule]] match {
        case publicSchedule :: Nil => publicSchedule should be(schedules.head)
        case other                 => fail(s"Unexpected response received: [$other]")
      }
    }
  }

  they should "respond with existing public schedules" in withRetry {
    val fixtures = new TestFixtures {}

    fixtures.scheduleStore.manage().put(schedules.head).await

    Get(s"/public/${schedules.head.id}") ~> fixtures.routes ~> check {
      status should be(StatusCodes.OK)
      responseAs[Schedule] should be(schedules.head)
    }
  }

  they should "fail if a public schedule is missing" in withRetry {
    val fixtures = new TestFixtures {}
    Get(s"/public/${Schedule.generateId()}") ~> fixtures.routes ~> check {
      status should be(StatusCodes.NotFound)
    }
  }

  they should "respond with existing schedules" in withRetry {
    val fixtures = new TestFixtures {}

    fixtures.scheduleStore.manage().put(schedules.head).await

    Get(s"/${schedules.head.id}") ~> fixtures.routes ~> check {
      status should be(StatusCodes.OK)
      responseAs[Schedule] should be(schedules.head)
    }
  }

  they should "fail if a schedule is missing" in withRetry {
    val fixtures = new TestFixtures {}
    Get(s"/${Schedule.generateId()}") ~> fixtures.routes ~> check {
      status should be(StatusCodes.NotFound)
    }
  }

  they should "update existing schedule" in withRetry {
    val fixtures = new TestFixtures {}
    fixtures.scheduleStore.manage().put(schedules.head).await

    Put(s"/${schedules.head.id}")
      .withEntity(updateRequest) ~> fixtures.routes ~> check {
      status should be(StatusCodes.OK)

      fixtures.scheduleStore
        .view()
        .get(schedules.head.id)
        .map(_.map(_.interval) should be(Some(updateRequest.interval)))
    }
  }

  they should "fail to update if a schedule is missing" in withRetry {
    val fixtures = new TestFixtures {}
    Put(s"/${Schedule.generateId()}")
      .withEntity(updateRequest) ~> fixtures.routes ~> check {
      status should be(StatusCodes.BadRequest)
    }
  }

  they should "delete existing schedules" in withRetry {
    val fixtures = new TestFixtures {}
    fixtures.scheduleStore.manage().put(schedules.head).await

    Delete(s"/${schedules.head.id}") ~> fixtures.routes ~> check {
      status should be(StatusCodes.OK)
      responseAs[DeletedSchedule] should be(DeletedSchedule(existing = true))

      fixtures.scheduleStore
        .view()
        .get(schedules.head.id)
        .map(_ should be(None))
    }
  }

  they should "not delete missing schedules" in withRetry {
    val fixtures = new TestFixtures {}

    Delete(s"/${schedules.head.id}") ~> fixtures.routes ~> check {
      status should be(StatusCodes.OK)
      responseAs[DeletedSchedule] should be(DeletedSchedule(existing = false))
    }
  }

  private implicit val typedSystem: ActorSystem[Nothing] = ActorSystem(
    Behaviors.ignore,
    "SchedulesSpec"
  )

  private implicit val untypedSystem: org.apache.pekko.actor.ActorSystem = typedSystem.classicSystem

  private implicit val log: Logger = LoggerFactory.getLogger(this.getClass.getName)

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
      interval = 3.seconds,
      created = Instant.now(),
      updated = Instant.now()
    ),
    Schedule(
      id = Schedule.generateId(),
      info = "test-schedule-02",
      isPublic = false,
      start = LocalDateTime.now(),
      interval = 3.hours,
      created = Instant.now(),
      updated = Instant.now()
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
