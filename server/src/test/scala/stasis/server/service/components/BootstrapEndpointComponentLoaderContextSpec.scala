package stasis.server.service.components

import scala.concurrent.duration._

import io.github.sndnv.layers.events.EventCollector
import io.github.sndnv.layers.security.oauth.OAuthClient
import io.github.sndnv.layers.telemetry.TelemetryContext
import io.github.sndnv.layers.testing.UnitSpec
import org.apache.pekko.actor.typed.ActorSystem
import org.apache.pekko.actor.typed.scaladsl.Behaviors
import org.apache.pekko.http.scaladsl.model.headers.HttpCredentials
import org.apache.pekko.util.Timeout
import org.mockito.scalatest.AsyncMockitoSugar

import stasis.core.security.NodeAuthenticator
import stasis.server.events.mocks.MockEventCollector
import stasis.server.security.CredentialsManagers
import stasis.server.security.ResourceProvider
import stasis.server.security.authenticators.UserAuthenticator
import stasis.server.security.mocks.MockDeviceCredentialsManager
import stasis.server.security.mocks.MockResourceProvider
import stasis.server.security.mocks.MockUserCredentialsManager
import stasis.server.service.ServerPersistence
import stasis.test.specs.unit.core.telemetry.MockTelemetryContext

class BootstrapEndpointComponentLoaderContextSpec extends UnitSpec with AsyncMockitoSugar {
  "A BootstrapEndpointComponentLoaderContext" should "provide its components" in {
    implicit val system: ActorSystem[Nothing] = ActorSystem(
      guardianBehavior = Behaviors.ignore,
      name = "BootstrapEndpointComponentLoaderContextSpec"
    )
    implicit val timeout: Timeout = 3.seconds
    implicit val telemetry: TelemetryContext = MockTelemetryContext()

    implicit val authenticators: Authenticators = Authenticators(
      instance = mock[OAuthClient],
      users = mock[UserAuthenticator],
      nodes = mock[NodeAuthenticator[HttpCredentials]]
    )
    implicit val credentialsManagers: CredentialsManagers = CredentialsManagers.Default(
      users = MockUserCredentialsManager(),
      devices = MockDeviceCredentialsManager()
    )
    implicit val serverPersistence: ServerPersistence = mock[ServerPersistence]
    implicit val eventCollector: EventCollector = MockEventCollector()
    implicit val resourceProvider: ResourceProvider = MockResourceProvider()

    val context: BootstrapEndpointComponentLoaderContext = implicitly

    context.base.components should be((system, timeout, telemetry))

    context.components should be(
      (authenticators, credentialsManagers, eventCollector, serverPersistence, resourceProvider)
    )
  }
}
