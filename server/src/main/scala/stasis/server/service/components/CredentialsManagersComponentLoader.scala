package stasis.server.service.components

import scala.concurrent.duration._

import com.typesafe.config.Config
import io.github.sndnv.layers.security.jwt.DefaultJwtProvider
import io.github.sndnv.layers.security.jwt.JwtProvider
import io.github.sndnv.layers.security.oauth.OAuthClient
import io.github.sndnv.layers.security.tls.EndpointContext
import io.github.sndnv.layers.service.components.Component
import io.github.sndnv.layers.service.components.ComponentLoader
import io.github.sndnv.layers.telemetry.TelemetryContext
import org.apache.pekko.actor.typed.ActorSystem
import org.apache.pekko.util.Timeout

import stasis.server.security.CredentialsManagers
import stasis.server.security.HttpCredentialsProvider
import stasis.server.security.devices.DeviceCredentialsManager
import stasis.server.security.devices.IdentityDeviceCredentialsManager
import stasis.server.security.users.IdentityUserCredentialsManager
import stasis.server.security.users.UserCredentialsManager

object CredentialsManagersComponentLoader
    extends ComponentLoader.Selectable[
      CredentialsManagers,
      (DefaultComponentContext, Authenticators)
    ] {
  override val name: String = "credentials-managers"
  override val component: Option[String] = None

  private val identityLoader = new ComponentLoader.TargetLoader.Configurable[
    CredentialsManagers,
    (DefaultComponentContext, Authenticators)
  ] {
    override val targetType: ComponentLoader.TargetType =
      ComponentLoader.TargetType.Custom(name = "identity")

    override def create(config: Config)(implicit
      context: ComponentLoader.Context[(DefaultComponentContext, Authenticators)]
    ): Component[CredentialsManagers] = {
      implicit val (base, authenticators) = context.value
      implicit val (system, timeout, telemetry) = base.components

      new IdentityCredentialsManagerComponent(
        config = IdentityCredentialsManagerComponent.Config(config)
      )
    }
  }

  private val dynamicLoader = new ComponentLoader.TargetLoader.Dynamic[
    CredentialsManagers,
    (DefaultComponentContext, Authenticators)
  ](allowed = true)

  override protected def underlying: Seq[
    ComponentLoader.TargetLoader[CredentialsManagers, (DefaultComponentContext, Authenticators)]
  ] = Seq(identityLoader, dynamicLoader)

  private class IdentityCredentialsManagerComponent(
    config: IdentityCredentialsManagerComponent.Config
  )(implicit
    system: ActorSystem[Nothing],
    timeout: Timeout,
    telemetry: TelemetryContext,
    authenticators: Authenticators
  ) extends Component[CredentialsManagers] {
    import system.executionContext

    override def renderConfig(withPrefix: String): String =
      s"""
         |$withPrefix  identity:
         |$withPrefix    url: ${config.url}
         |$withPrefix    management:
         |$withPrefix      user:                 ${config.managementUser}
         |$withPrefix      password-provided:    ${config.managementUserPassword.nonEmpty.toString}
         |$withPrefix      scope:                ${config.managementUserScope}
         |$withPrefix      expiration-tolerance: ${config.managementTokenExpirationTolerance.toCoarsest.toString}
         |$withPrefix    client:
         |$withPrefix      redirect-uri:     ${config.clientRedirectUri}
         |$withPrefix      token-expiration: ${config.clientTokenExpiration.toCoarsest.toString}""".stripMargin

    override val component: CredentialsManagers = {
      val identityCredentialsManagerJwtProvider: JwtProvider = DefaultJwtProvider(
        client = authenticators.instance,
        clientParameters = OAuthClient.GrantParameters.ResourceOwnerPasswordCredentials(
          username = config.managementUser,
          password = config.managementUserPassword
        ),
        expirationTolerance = config.managementTokenExpirationTolerance
      )

      val userCredentialsManager: UserCredentialsManager = IdentityUserCredentialsManager(
        identityUrl = config.url,
        identityCredentials = HttpCredentialsProvider.Default(
          scope = config.managementUserScope,
          underlying = identityCredentialsManagerJwtProvider
        ),
        context = config.context
      )

      val deviceCredentialsManager: DeviceCredentialsManager = IdentityDeviceCredentialsManager(
        identityUrl = config.url,
        identityCredentials = HttpCredentialsProvider.Default(
          scope = config.managementUserScope,
          underlying = identityCredentialsManagerJwtProvider
        ),
        redirectUri = config.clientRedirectUri,
        tokenExpiration = config.clientTokenExpiration,
        context = config.context
      )

      CredentialsManagers.Default(
        users = userCredentialsManager,
        devices = deviceCredentialsManager
      )
    }
  }

  private object IdentityCredentialsManagerComponent {
    final case class Config(
      url: String,
      managementUser: String,
      managementUserPassword: String,
      managementUserScope: String,
      managementTokenExpirationTolerance: FiniteDuration,
      clientRedirectUri: String,
      clientTokenExpiration: FiniteDuration,
      context: Option[EndpointContext]
    )

    object Config {
      def apply(config: com.typesafe.config.Config): Config =
        Config(
          url = config.getString("identity.url"),
          managementUser = config.getString("identity.management.user"),
          managementUserPassword = config.getString("identity.management.user-password"),
          managementUserScope = config.getString("identity.management.scope"),
          managementTokenExpirationTolerance = config
            .getDuration("identity.management.token-expiration-tolerance")
            .toMillis
            .millis,
          clientRedirectUri = config.getString("identity.client.redirect-uri"),
          clientTokenExpiration = config.getDuration("identity.client.token-expiration").toMillis.millis,
          context = EndpointContext(config.getConfig("identity.context"))
        )
    }
  }
}
