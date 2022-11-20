package stasis.test.specs.unit.server.api.routes

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorSystem, Behavior, SpawnProtocol}
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.testkit.ScalatestRouteTest
import org.slf4j.{Logger, LoggerFactory}
import stasis.core.packaging.{Crate, Manifest}
import stasis.core.persistence.manifests.ManifestStore
import stasis.core.telemetry.TelemetryContext
import stasis.server.api.routes.{Manifests, RoutesContext}
import stasis.server.model.manifests.ServerManifestStore
import stasis.server.security.{CurrentUser, ResourceProvider}
import stasis.shared.api.responses.DeletedManifest
import stasis.shared.model.users.User
import stasis.test.specs.unit.AsyncUnitSpec
import stasis.test.specs.unit.core.persistence.Generators
import stasis.test.specs.unit.core.persistence.mocks.MockManifestStore
import stasis.test.specs.unit.core.telemetry.MockTelemetryContext
import stasis.test.specs.unit.server.security.mocks.MockResourceProvider

class ManifestsSpec extends AsyncUnitSpec with ScalatestRouteTest {
  import de.heikoseeberger.akkahttpplayjson.PlayJsonSupport._
  import stasis.core.api.Formats._
  import stasis.shared.api.Formats._

  "Manifests routes" should "respond with existing manifests" in {
    val fixtures = new TestFixtures {}
    val manifest = Generators.generateManifest
    fixtures.manifestStore.put(manifest).await

    Get(s"/${manifest.crate}") ~> fixtures.routes ~> check {
      status should be(StatusCodes.OK)
      responseAs[Manifest] should be(manifest)
    }
  }

  they should "fail if a manifest is missing" in {
    val fixtures = new TestFixtures {}

    Get(s"/${Crate.generateId()}") ~> fixtures.routes ~> check {
      status should be(StatusCodes.NotFound)
    }
  }

  they should "delete existing manifests" in {
    val fixtures = new TestFixtures {}
    val manifest = Generators.generateManifest
    fixtures.manifestStore.put(manifest).await

    Delete(s"/${manifest.crate}") ~> fixtures.routes ~> check {
      status should be(StatusCodes.OK)
      responseAs[DeletedManifest] should be(DeletedManifest(existing = true))
    }
  }

  they should "not delete missing manifest" in {
    val fixtures = new TestFixtures {}

    Delete(s"/${Crate.generateId()}") ~> fixtures.routes ~> check {
      status should be(StatusCodes.OK)
      responseAs[DeletedManifest] should be(DeletedManifest(existing = false))
    }
  }

  private implicit val typedSystem: ActorSystem[SpawnProtocol.Command] = ActorSystem(
    Behaviors.setup(_ => SpawnProtocol()): Behavior[SpawnProtocol.Command],
    "ManifestsSpec"
  )

  private implicit val untypedSystem: akka.actor.ActorSystem = typedSystem.classicSystem

  private implicit val log: Logger = LoggerFactory.getLogger(this.getClass.getName)

  private implicit val telemetry: TelemetryContext = MockTelemetryContext()

  private trait TestFixtures {
    lazy val manifestStore: ManifestStore = new MockManifestStore()

    lazy val serverManifestStore: ServerManifestStore = ServerManifestStore(manifestStore)

    lazy implicit val provider: ResourceProvider = new MockResourceProvider(
      resources = Set(
        serverManifestStore.view(),
        serverManifestStore.manage()
      )
    )

    lazy implicit val context: RoutesContext = RoutesContext.collect()

    lazy val routes: Route = new Manifests().routes
  }

  private implicit val user: CurrentUser = CurrentUser(User.generateId())
}
