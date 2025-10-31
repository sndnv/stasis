package stasis.server.service.components

import io.github.sndnv.layers.events.EventCollector
import io.github.sndnv.layers.security.oauth.OAuthClient
import io.github.sndnv.layers.service.components.auto._
import io.github.sndnv.layers.telemetry.TelemetryContext
import io.github.sndnv.layers.testing.UnitSpec
import org.apache.pekko.actor.typed.ActorSystem
import org.apache.pekko.actor.typed.scaladsl.Behaviors
import org.apache.pekko.http.scaladsl.model.headers.HttpCredentials
import org.mockito.scalatest.AsyncMockitoSugar

import stasis.core.security.NodeAuthenticator
import stasis.server.events.mocks.MockEventCollector
import stasis.server.routing.ServerRouter
import stasis.server.security.authenticators.UserAuthenticator
import stasis.server.service.CorePersistence
import stasis.test.specs.unit.core.persistence.manifests.MockManifestStore
import stasis.test.specs.unit.core.persistence.nodes.MockNodeStore
import stasis.test.specs.unit.core.persistence.reservations.MockReservationStore
import stasis.test.specs.unit.core.telemetry.MockTelemetryContext

class RoutingComponentLoaderSpec extends UnitSpec with AsyncMockitoSugar {
  "An RoutingComponentLoader" should "provide its name and component name" in {
    RoutingComponentLoader.name should be("routing")
    RoutingComponentLoader.component should be(Some("router"))
  }

  it should "create its component and render its config (default)" in {
    implicit val corePersistence: CorePersistence = mock[CorePersistence]

    implicit val authenticators: Authenticators = Authenticators(
      instance = mock[OAuthClient],
      users = mock[UserAuthenticator],
      nodes = mock[NodeAuthenticator[HttpCredentials]]
    )

    implicit val events: EventCollector = MockEventCollector()

    when(corePersistence.nodes) thenReturn MockNodeStore()
    when(corePersistence.manifests) thenReturn MockManifestStore()
    when(corePersistence.reservations) thenReturn MockReservationStore()
    when(corePersistence.staging) thenReturn None

    val component = RoutingComponentLoader.create(config = config)

    component.component should be(an[ServerRouter])

    val rendered = component.renderConfig(withPrefix = "")

    rendered should include("router-id: 365373c6-217a-4398-a83a-6098aa5d92ab")
    rendered should include("clients:")
    rendered should include("authentication:")
    rendered should include("token-expiration-tolerance: 30 seconds")
    rendered should include("core:")
    rendered should include("max-chunk-size: 8192")
    rendered should include("http:")
    rendered should include("request-buffer-size: 1000")
    rendered should include("retry:")
    rendered should include("min-backoff:   500 milliseconds")
    rendered should include("max-backoff:   3 seconds")
    rendered should include("random-factor: 0.1")
    rendered should include("max-retries:   5")
  }

  private implicit val typedSystem: ActorSystem[Nothing] = ActorSystem(
    guardianBehavior = Behaviors.ignore,
    name = "RoutingComponentLoader"
  )

  private implicit val telemetry: TelemetryContext = MockTelemetryContext()

  private val config = typedSystem.settings.config.getConfig("stasis.test.server.service.components.routing")
}
