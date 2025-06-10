package stasis.server.api.routes

import scala.concurrent.Future

import org.apache.pekko.actor.typed.ActorSystem
import org.apache.pekko.actor.typed.scaladsl.Behaviors
import org.apache.pekko.http.scaladsl.marshalling.Marshal
import org.apache.pekko.http.scaladsl.model.RequestEntity
import org.apache.pekko.http.scaladsl.model.StatusCodes
import org.apache.pekko.http.scaladsl.server.Route
import org.apache.pekko.http.scaladsl.testkit.ScalatestRouteTest
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import stasis.layers.telemetry.ApplicationInformation
import stasis.layers.telemetry.analytics.AnalyticsEntry
import stasis.server.persistence.analytics.AnalyticsEntryStore
import stasis.server.persistence.analytics.MockAnalyticsEntryStore
import stasis.server.security.CurrentUser
import stasis.server.security.ResourceProvider
import stasis.server.security.mocks.MockResourceProvider
import stasis.shared.api.requests.CreateAnalyticsEntry
import stasis.shared.api.responses.CreatedAnalyticsEntry
import stasis.shared.api.responses.DeletedAnalyticsEntry
import stasis.shared.model.analytics.StoredAnalyticsEntry
import stasis.shared.model.users.User
import stasis.test.specs.unit.AsyncUnitSpec

class AnalyticsSpec extends AsyncUnitSpec with ScalatestRouteTest {
  import com.github.pjfanning.pekkohttpplayjson.PlayJsonSupport._

  import stasis.shared.api.Formats._

  "Analytics routes" should "respond with all entries" in withRetry {
    val fixtures = new TestFixtures {}
    Future.sequence(entries.map(fixtures.analyticsStore.manageSelf().create)).await

    Get("/") ~> fixtures.routes ~> check {
      status should be(StatusCodes.OK)
      responseAs[Seq[Analytics.AnalyticsEntrySummary]] should contain theSameElementsAs entries.map(
        Analytics.AnalyticsEntrySummary.fromEntry
      )
    }
  }

  they should "create new entries" in withRetry {
    val fixtures = new TestFixtures {}
    Post("/").withEntity(createRequest) ~> fixtures.routes ~> check {
      status should be(StatusCodes.OK)

      fixtures.analyticsStore
        .view()
        .get(entityAs[CreatedAnalyticsEntry].entry)
        .map(_.isDefined should be(true))
    }
  }

  they should "respond with existing entries" in withRetry {
    val fixtures = new TestFixtures {}

    fixtures.analyticsStore.manageSelf().create(entries.head).await

    Get(s"/${entries.head.id}") ~> fixtures.routes ~> check {
      status should be(StatusCodes.OK)
      responseAs[StoredAnalyticsEntry] should be(entries.head)
    }
  }

  they should "fail if an entry is missing" in withRetry {
    val fixtures = new TestFixtures {}
    Get(s"/${StoredAnalyticsEntry.generateId()}") ~> fixtures.routes ~> check {
      status should be(StatusCodes.NotFound)
    }
  }

  they should "delete existing entries" in withRetry {
    val fixtures = new TestFixtures {}
    fixtures.analyticsStore.manageSelf().create(entries.head).await

    Delete(s"/${entries.head.id}") ~> fixtures.routes ~> check {
      status should be(StatusCodes.OK)
      responseAs[DeletedAnalyticsEntry] should be(DeletedAnalyticsEntry(existing = true))

      fixtures.analyticsStore
        .view()
        .get(entries.head.id)
        .map(_ should be(None))
    }
  }

  they should "not delete missing entries" in withRetry {
    val fixtures = new TestFixtures {}

    Delete(s"/${entries.head.id}") ~> fixtures.routes ~> check {
      status should be(StatusCodes.OK)
      responseAs[DeletedAnalyticsEntry] should be(DeletedAnalyticsEntry(existing = false))
    }
  }

  private implicit val typedSystem: ActorSystem[Nothing] = ActorSystem(
    guardianBehavior = Behaviors.ignore,
    name = "AnalyticsSpec"
  )

  private implicit val log: Logger = LoggerFactory.getLogger(this.getClass.getName)

  private implicit val user: CurrentUser = CurrentUser(User.generateId())

  private trait TestFixtures {
    lazy val analyticsStore: AnalyticsEntryStore = MockAnalyticsEntryStore()

    implicit lazy val provider: ResourceProvider = new MockResourceProvider(
      resources = Set(
        analyticsStore.view(),
        analyticsStore.manage(),
        analyticsStore.manageSelf()
      )
    )

    lazy implicit val context: RoutesContext = RoutesContext.collect()

    lazy val routes: Route = new Analytics().routes
  }

  private val entries = Seq(
    AnalyticsEntry.collected(app = ApplicationInformation.none),
    AnalyticsEntry.collected(app = ApplicationInformation.none),
    AnalyticsEntry.collected(app = ApplicationInformation.none)
  ).map(CreateAnalyticsEntry.apply(_).toStoredAnalyticsEntry)

  private val createRequest = CreateAnalyticsEntry(
    entry = AnalyticsEntry.collected(app = ApplicationInformation.none)
  )

  import scala.language.implicitConversions

  private implicit def createRequestToEntity(request: CreateAnalyticsEntry): RequestEntity =
    Marshal(request).to[RequestEntity].await
}
