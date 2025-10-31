package stasis.server.service.components

import io.github.sndnv.layers.security.oauth.DefaultOAuthClient
import io.github.sndnv.layers.service.components.auto._
import io.github.sndnv.layers.telemetry.TelemetryContext
import io.github.sndnv.layers.testing.UnitSpec
import org.apache.pekko.actor.typed.ActorSystem
import org.apache.pekko.actor.typed.scaladsl.Behaviors
import org.mockito.scalatest.AsyncMockitoSugar

import stasis.core.security.JwtNodeAuthenticator
import stasis.server.persistence.users.MockUserStore
import stasis.server.security.authenticators.DefaultUserAuthenticator
import stasis.server.service.CorePersistence
import stasis.server.service.ServerPersistence
import stasis.test.specs.unit.core.persistence.nodes.MockNodeStore
import stasis.test.specs.unit.core.telemetry.MockTelemetryContext

class AuthenticatorsComponentLoaderSpec extends UnitSpec with AsyncMockitoSugar {
  "An AuthenticatorsComponentLoader" should "provide its name and component name" in {
    AuthenticatorsComponentLoader.name should be("authenticators")
    AuthenticatorsComponentLoader.component should be(None)
  }

  it should "create its component and render its config (default)" in {
    implicit val corePersistence: CorePersistence = mock[CorePersistence]
    implicit val serverPersistence: ServerPersistence = mock[ServerPersistence]

    when(corePersistence.nodes) thenReturn MockNodeStore()
    when(serverPersistence.users) thenReturn MockUserStore()

    val component = AuthenticatorsComponentLoader.create(config = config)

    component.component.users should be(an[DefaultUserAuthenticator])
    component.component.nodes should be(an[JwtNodeAuthenticator])
    component.component.instance should be(an[DefaultOAuthClient])

    val rendered = component.renderConfig(withPrefix = "")

    rendered should include("users:")
    rendered should include("issuer:                 test-issuer-1")
    rendered should include("audience:               test-audience-1")
    rendered should include("identity-claim:         sub-1")
    rendered should include("jwks-endpoint:          https://localhost:9090/jwks/jwks.json")
    rendered should include("refresh-interval:       90 minutes")
    rendered should include("refresh-retry-interval: 3 seconds")
    rendered should include("expiration-tolerance:   30 seconds")
    rendered should include("nodes:")
    rendered should include("issuer:                 test-issuer-2")
    rendered should include("audience:               test-audience-2")
    rendered should include("identity-claim:         sub-2")
    rendered should include("jwks-endpoint:          https://localhost:9091/jwks/jwks.json")
    rendered should include("refresh-interval:       90 minutes")
    rendered should include("refresh-retry-interval: 3 seconds")
    rendered should include("expiration-tolerance:   30 seconds")
    rendered should include("instance:")
    rendered should include("token-endpoint:         https://localhost:9090/oauth/token")
    rendered should include("client:                 test-client-id")
    rendered should include("use-query-string:       false")
  }

  private implicit val typedSystem: ActorSystem[Nothing] = ActorSystem(
    guardianBehavior = Behaviors.ignore,
    name = "AuthenticatorsComponentLoaderSpec"
  )

  private implicit val telemetry: TelemetryContext = MockTelemetryContext()

  private val config = typedSystem.settings.config.getConfig("stasis.test.server.service.components.authenticators")
}
