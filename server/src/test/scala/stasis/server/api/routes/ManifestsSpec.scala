package stasis.server.api.routes

import org.apache.pekko.actor.typed.ActorSystem
import org.apache.pekko.actor.typed.scaladsl.Behaviors
import org.apache.pekko.http.scaladsl.model.StatusCodes
import org.apache.pekko.http.scaladsl.server.Route
import org.apache.pekko.http.scaladsl.testkit.ScalatestRouteTest
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import stasis.core.packaging.Crate
import stasis.core.packaging.Manifest
import stasis.core.persistence.manifests.ManifestStore
import stasis.server.events.mocks.MockEventCollector
import stasis.server.persistence.manifests.ServerManifestStore
import stasis.server.security.CurrentUser
import stasis.server.security.ResourceProvider
import stasis.server.security.mocks.MockResourceProvider
import stasis.shared.api.responses.DeletedManifest
import stasis.shared.model.users.User
import stasis.test.specs.unit.AsyncUnitSpec
import stasis.test.specs.unit.core.persistence.Generators
import stasis.test.specs.unit.core.persistence.manifests.MockManifestStore

class ManifestsSpec extends AsyncUnitSpec with ScalatestRouteTest {
  import com.github.pjfanning.pekkohttpplayjson.PlayJsonSupport._

  import stasis.core.api.Formats._
  import stasis.shared.api.Formats._

  "Manifests routes" should "respond with existing manifests" in withRetry {
    val fixtures = new TestFixtures {}
    val manifest = Generators.generateManifest
    fixtures.manifestStore.put(manifest).await

    Get(s"/${manifest.crate}") ~> fixtures.routes ~> check {
      status should be(StatusCodes.OK)
      responseAs[Manifest] should be(manifest)

      fixtures.eventCollector.events should be(empty)
    }
  }

  they should "fail if a manifest is missing" in withRetry {
    val fixtures = new TestFixtures {}

    Get(s"/${Crate.generateId()}") ~> fixtures.routes ~> check {
      status should be(StatusCodes.NotFound)

      fixtures.eventCollector.events should be(empty)
    }
  }

  they should "delete existing manifests" in withRetry {
    val fixtures = new TestFixtures {}
    val manifest = Generators.generateManifest
    fixtures.manifestStore.put(manifest).await

    Delete(s"/${manifest.crate}") ~> fixtures.routes ~> check {
      status should be(StatusCodes.OK)
      responseAs[DeletedManifest] should be(DeletedManifest(existing = true))

      fixtures.eventCollector.events should be(empty)
    }
  }

  they should "not delete missing manifest" in withRetry {
    val fixtures = new TestFixtures {}

    Delete(s"/${Crate.generateId()}") ~> fixtures.routes ~> check {
      status should be(StatusCodes.OK)
      responseAs[DeletedManifest] should be(DeletedManifest(existing = false))

      fixtures.eventCollector.events should be(empty)
    }
  }

  private implicit val typedSystem: ActorSystem[Nothing] = ActorSystem(
    guardianBehavior = Behaviors.ignore,
    name = "ManifestsSpec"
  )

  private implicit val untypedSystem: org.apache.pekko.actor.ActorSystem = typedSystem.classicSystem

  private implicit val log: Logger = LoggerFactory.getLogger(this.getClass.getName)

  private trait TestFixtures {
    lazy val manifestStore: ManifestStore = MockManifestStore()

    lazy val serverManifestStore: ServerManifestStore = ServerManifestStore(manifestStore)

    lazy implicit val provider: ResourceProvider = new MockResourceProvider(
      resources = Set(
        serverManifestStore.view(),
        serverManifestStore.manage()
      )
    )

    lazy implicit val eventCollector: MockEventCollector = MockEventCollector()

    lazy implicit val context: RoutesContext = RoutesContext.collect()

    lazy val routes: Route = new Manifests().routes
  }

  private implicit val user: CurrentUser = CurrentUser(User.generateId())
}
