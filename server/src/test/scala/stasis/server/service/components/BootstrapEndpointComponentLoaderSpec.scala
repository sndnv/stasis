package stasis.server.service.components

import com.typesafe.config.ConfigFactory
import io.github.sndnv.layers.events.EventCollector
import io.github.sndnv.layers.security.oauth.OAuthClient
import io.github.sndnv.layers.service.components.auto._
import io.github.sndnv.layers.telemetry.TelemetryContext
import io.github.sndnv.layers.testing.UnitSpec
import org.apache.pekko.actor.typed.ActorSystem
import org.apache.pekko.actor.typed.scaladsl.Behaviors
import org.apache.pekko.http.scaladsl.model.headers.HttpCredentials
import org.mockito.scalatest.AsyncMockitoSugar
import play.api.libs.json.Json

import stasis.core.security.NodeAuthenticator
import stasis.server.api.BootstrapEndpoint
import stasis.server.events.mocks.MockEventCollector
import stasis.server.persistence.devices.MockDeviceBootstrapCodeStore
import stasis.server.security.CredentialsManagers
import stasis.server.security.ResourceProvider
import stasis.server.security.authenticators.UserAuthenticator
import stasis.server.security.mocks.MockDeviceCredentialsManager
import stasis.server.security.mocks.MockResourceProvider
import stasis.server.security.mocks.MockUserCredentialsManager
import stasis.server.service.ServerPersistence
import stasis.test.specs.unit.core.telemetry.MockTelemetryContext

class BootstrapEndpointComponentLoaderSpec extends UnitSpec with AsyncMockitoSugar {
  "A BootstrapEndpointComponentLoader" should "provide its name and component name" in {
    BootstrapEndpointComponentLoader.name should be("bootstrap")
    BootstrapEndpointComponentLoader.component should be(None)
  }

  it should "create its component and render its config (default)" in {
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

    when(serverPersistence.deviceBootstrapCodes).thenAnswer(MockDeviceBootstrapCodeStore())

    implicit val events: EventCollector = MockEventCollector()
    implicit val resourceProvider: ResourceProvider = MockResourceProvider()

    val component = BootstrapEndpointComponentLoader.create(config = config)

    component.component should be(a[BootstrapEndpoint.Default])

    val rendered = component.renderConfig(withPrefix = "")

    rendered should include("api:")
    rendered should include("interface:")
    rendered should include("port:")
    rendered should include("context:")
    rendered should include("enabled:")
    rendered should include("protocol:")
    rendered should include("keystore:")

    rendered should include("devices:")
    rendered should include("code-size:")
    rendered should include("code-expiration:")
    rendered should include("secret-size:")
    rendered should include("parameters:")
    rendered should include("authentication:")
    rendered should include("token-endpoint:")
    rendered should include("use-query-string:")
    rendered should include("scopes-api:")
    rendered should include("scopes-core:")
    rendered should include("server-api:")
    rendered should include("url:")
    rendered should include("context-enabled:")
    rendered should include("context-protocol:")
    rendered should include("server-core:")
    rendered should include("address:")
    rendered should include("context-enabled:")
    rendered should include("context-protocol:")
    rendered should include("secrets:")
    rendered should include("derivation:")
    rendered should include("encryption:")
    rendered should include("secret-size:")
    rendered should include("iterations:")
    rendered should include("salt-prefix:")
    rendered should include("authentication:")
    rendered should include("enabled:")
    rendered should include("secret-size:")
    rendered should include("iterations:")
    rendered should include("salt-prefix:")
    rendered should include("encryption:")
    rendered should include("file:")
    rendered should include("key-size:")
    rendered should include("iv-size:")
    rendered should include("metadata:")
    rendered should include("key-size:")
    rendered should include("iv-size:")
    rendered should include("device-secret:")
    rendered should include("key-size:")
    rendered should include("iv-size:")
    rendered should include("additional-config:")
  }

  it should "load device bootstrap params with additional config" in {
    val params = BootstrapEndpointComponentLoader.DefaultBootstrapEndpointComponent
      .Config(
        config = ConfigFactory
          .load("application-device-bootstrap")
          .getConfig("stasis.server.bootstrap")
      )
      .devices
      .parameters

    params.authentication.tokenEndpoint should be("http://localhost:28998/oauth/token")
    params.authentication.clientId shouldBe empty
    params.authentication.clientSecret shouldBe empty
    params.authentication.useQueryString should be(false)
    params.authentication.scopes.api should be("urn:stasis:identity:audience:stasis-server-test")
    params.authentication.scopes.core should be("urn:stasis:identity:audience:stasis-server-test")
    params.authentication.context.enabled should be(true)
    params.authentication.context.protocol should be("TLS")
    params.serverApi.url should be("https://localhost:28999")
    params.serverApi.user shouldBe empty
    params.serverApi.userSalt shouldBe empty
    params.serverApi.device shouldBe empty
    params.serverApi.context.enabled should be(false)
    params.serverApi.context.protocol should be("TLS")
    params.serverCore.address should be("https://localhost:38999")
    params.serverCore.context.enabled should be(false)
    params.serverCore.context.protocol should be("TLS")
    params.additionalConfig should be(Json.parse("""{"a":{"b":{"c":"d","e":1,"f":["g","h"]}}}"""))
  }

  it should "load device bootstrap params without additional config" in {
    val params = BootstrapEndpointComponentLoader.DefaultBootstrapEndpointComponent
      .Config(
        config = typedSystem.settings.config.getConfig("stasis.server.bootstrap")
      )
      .devices
      .parameters

    params.authentication.tokenEndpoint should be("http://localhost:29998/oauth/token")
    params.authentication.clientId shouldBe empty
    params.authentication.clientSecret shouldBe empty
    params.authentication.useQueryString should be(false)
    params.authentication.scopes.api should be("urn:stasis:identity:audience:stasis-server-test")
    params.authentication.scopes.core should be("urn:stasis:identity:audience:stasis-server-test")
    params.authentication.context.enabled should be(true)
    params.authentication.context.protocol should be("TLS")
    params.serverApi.url should be("https://localhost:39999")
    params.serverApi.user shouldBe empty
    params.serverApi.userSalt shouldBe empty
    params.serverApi.device shouldBe empty
    params.serverApi.context.enabled should be(false)
    params.serverApi.context.protocol should be("TLS")
    params.serverCore.address should be("https://localhost:49999")
    params.serverCore.context.enabled should be(false)
    params.serverCore.context.protocol should be("TLS")
    params.additionalConfig should be(Json.parse("{}"))
  }

  private implicit val typedSystem: ActorSystem[Nothing] = ActorSystem(
    guardianBehavior = Behaviors.ignore,
    name = "BootstrapEndpointComponentLoaderSpec"
  )

  private implicit val telemetry: TelemetryContext = MockTelemetryContext()

  private val config = typedSystem.settings.config.getConfig("stasis.test.server.service.components.bootstrap")
}
