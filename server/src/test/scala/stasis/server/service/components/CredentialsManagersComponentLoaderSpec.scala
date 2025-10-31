package stasis.server.service.components

import io.github.sndnv.layers.security.oauth.OAuthClient
import io.github.sndnv.layers.service.components.auto._
import io.github.sndnv.layers.telemetry.TelemetryContext
import io.github.sndnv.layers.testing.UnitSpec
import org.apache.pekko.actor.typed.ActorSystem
import org.apache.pekko.actor.typed.scaladsl.Behaviors
import org.apache.pekko.http.scaladsl.model.headers.HttpCredentials
import org.mockito.scalatest.AsyncMockitoSugar

import stasis.core.security.NodeAuthenticator
import stasis.server.security.authenticators.UserAuthenticator
import stasis.server.security.devices.IdentityDeviceCredentialsManager
import stasis.server.security.users.IdentityUserCredentialsManager
import stasis.test.specs.unit.core.telemetry.MockTelemetryContext

class CredentialsManagersComponentLoaderSpec extends UnitSpec with AsyncMockitoSugar {
  "A CredentialsManagersComponentLoader" should "provide its name and component name" in {
    CredentialsManagersComponentLoader.name should be("credentials-managers")
    CredentialsManagersComponentLoader.component should be(None)
  }

  it should "create its component and render its config (identity)" in {
    implicit val authenticators: Authenticators = Authenticators(
      instance = mock[OAuthClient],
      users = mock[UserAuthenticator],
      nodes = mock[NodeAuthenticator[HttpCredentials]]
    )

    val component = CredentialsManagersComponentLoader.create(config = config)

    component.component.users should be(an[IdentityUserCredentialsManager])
    component.component.devices should be(an[IdentityDeviceCredentialsManager])

    val rendered = component.renderConfig(withPrefix = "")

    rendered should include("identity:")
    rendered should include("url:")
    rendered should include("management:")
    rendered should include("user:")
    rendered should include("password-provided:")
    rendered should include("scope:")
    rendered should include("expiration-tolerance:")
    rendered should include("client:")
    rendered should include("redirect-uri:")
    rendered should include("token-expiration:")
  }

  private implicit val typedSystem: ActorSystem[Nothing] = ActorSystem(
    guardianBehavior = Behaviors.ignore,
    name = "CredentialsManagersComponentLoaderSpec"
  )

  private implicit val telemetry: TelemetryContext = MockTelemetryContext()

  private val config = typedSystem.settings.config.getConfig("stasis.test.server.service.components.credentials-managers")
}
