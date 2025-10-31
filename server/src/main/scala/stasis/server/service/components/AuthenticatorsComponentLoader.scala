package stasis.server.service.components

import scala.concurrent.duration._

import com.typesafe.config.Config
import io.github.sndnv.layers.security.jwt.DefaultJwtAuthenticator
import io.github.sndnv.layers.security.keys.RemoteKeyProvider
import io.github.sndnv.layers.security.oauth.DefaultOAuthClient
import io.github.sndnv.layers.security.tls.EndpointContext
import io.github.sndnv.layers.service.components.Component
import io.github.sndnv.layers.service.components.ComponentLoader
import io.github.sndnv.layers.telemetry.TelemetryContext
import org.apache.pekko.actor.typed.ActorSystem
import org.apache.pekko.util.Timeout

import stasis.core.security.JwtNodeAuthenticator
import stasis.server.security.authenticators.DefaultUserAuthenticator
import stasis.server.service.CorePersistence
import stasis.server.service.ServerPersistence

object AuthenticatorsComponentLoader
    extends ComponentLoader.Fixed[
      Authenticators,
      (DefaultComponentContext, CorePersistence, ServerPersistence)
    ] {
  override val name: String = "authenticators"
  override val component: Option[String] = None

  override protected def default(
    config: Config
  )(implicit
    context: ComponentLoader.Context[(DefaultComponentContext, CorePersistence, ServerPersistence)]
  ): Component[Authenticators] = {
    val (base, corePersistence, serverPersistence) = context.value
    implicit val (system, timeout, telemetry) = base.components

    new AuthenticatorsComponent(
      config = AuthenticatorsComponent.Config(config = config),
      corePersistence = corePersistence,
      serverPersistence = serverPersistence
    )
  }

  private class AuthenticatorsComponent(
    config: AuthenticatorsComponent.Config,
    corePersistence: CorePersistence,
    serverPersistence: ServerPersistence
  )(implicit system: ActorSystem[Nothing], timeout: Timeout, telemetry: TelemetryContext)
      extends Component[Authenticators] {
    import system.executionContext

    override def renderConfig(withPrefix: String): String =
      s"""
         |$withPrefix  users:
         |$withPrefix    issuer:                 ${config.users.issuer}
         |$withPrefix    audience:               ${config.users.audience}
         |$withPrefix    identity-claim:         ${config.users.identityClaim}
         |$withPrefix    jwks-endpoint:          ${config.users.jwksEndpoint}
         |$withPrefix    refresh-interval:       ${config.users.refreshInterval.toCoarsest.toString}
         |$withPrefix    refresh-retry-interval: ${config.users.refreshRetryInterval.toCoarsest.toString}
         |$withPrefix    expiration-tolerance:   ${config.users.expirationTolerance.toCoarsest.toString}
         |$withPrefix  nodes:
         |$withPrefix    issuer:                 ${config.nodes.issuer}
         |$withPrefix    audience:               ${config.nodes.audience}
         |$withPrefix    identity-claim:         ${config.nodes.identityClaim}
         |$withPrefix    jwks-endpoint:          ${config.nodes.jwksEndpoint}
         |$withPrefix    refresh-interval:       ${config.nodes.refreshInterval.toCoarsest.toString}
         |$withPrefix    refresh-retry-interval: ${config.nodes.refreshRetryInterval.toCoarsest.toString}
         |$withPrefix    expiration-tolerance:   ${config.nodes.expirationTolerance.toCoarsest.toString}
         |$withPrefix  instance:
         |$withPrefix    token-endpoint:         ${config.instance.tokenEndpoint}
         |$withPrefix    client:                 ${config.instance.client}
         |$withPrefix    use-query-string:       ${config.instance.useQueryString.toString}""".stripMargin

    override val component: Authenticators =
      Authenticators(
        instance = DefaultOAuthClient(
          tokenEndpoint = config.instance.tokenEndpoint,
          client = config.instance.client,
          clientSecret = config.instance.clientSecret,
          useQueryString = config.instance.useQueryString,
          context = config.instance.context
        ),
        users = DefaultUserAuthenticator(
          store = serverPersistence.users.view(),
          underlying = DefaultJwtAuthenticator(
            provider = RemoteKeyProvider(
              jwksEndpoint = config.users.jwksEndpoint,
              context = config.users.context,
              refreshInterval = config.users.refreshInterval,
              refreshRetryInterval = config.users.refreshRetryInterval,
              issuer = config.users.issuer
            ),
            audience = config.users.audience,
            identityClaim = config.users.identityClaim,
            expirationTolerance = config.users.expirationTolerance
          )
        ),
        nodes = JwtNodeAuthenticator(
          nodeStore = corePersistence.nodes.view,
          underlying = DefaultJwtAuthenticator(
            provider = RemoteKeyProvider(
              jwksEndpoint = config.nodes.jwksEndpoint,
              context = config.nodes.context,
              refreshInterval = config.nodes.refreshInterval,
              refreshRetryInterval = config.nodes.refreshRetryInterval,
              issuer = config.nodes.issuer
            ),
            audience = config.nodes.audience,
            identityClaim = config.nodes.identityClaim,
            expirationTolerance = config.nodes.expirationTolerance
          )
        )
      )
  }

  private object AuthenticatorsComponent {
    final case class Config(
      instance: Config.Instance,
      users: Config.Users,
      nodes: Config.Nodes
    )

    object Config {
      final case class Instance(
        tokenEndpoint: String,
        client: String,
        clientSecret: String,
        useQueryString: Boolean,
        context: Option[EndpointContext]
      )

      final case class Users(
        issuer: String,
        audience: String,
        identityClaim: String,
        jwksEndpoint: String,
        refreshInterval: FiniteDuration,
        refreshRetryInterval: FiniteDuration,
        expirationTolerance: FiniteDuration,
        context: Option[EndpointContext]
      )

      final case class Nodes(
        issuer: String,
        audience: String,
        identityClaim: String,
        jwksEndpoint: String,
        refreshInterval: FiniteDuration,
        refreshRetryInterval: FiniteDuration,
        expirationTolerance: FiniteDuration,
        context: Option[EndpointContext]
      )

      def apply(config: com.typesafe.config.Config): Config =
        Config(
          instance = Config.Instance(
            tokenEndpoint = config.getString("instance.token-endpoint"),
            client = config.getString("instance.client-id"),
            clientSecret = config.getString("instance.client-secret"),
            useQueryString = config.getBoolean("instance.use-query-string"),
            context = EndpointContext(config.getConfig("instance.context"))
          ),
          users = Config.Users(
            issuer = config.getString("users.issuer"),
            audience = config.getString("users.audience"),
            identityClaim = config.getString("users.identity-claim"),
            jwksEndpoint = config.getString("users.jwks-endpoint"),
            refreshInterval = config.getDuration("users.refresh-interval").toMillis.millis,
            refreshRetryInterval = config.getDuration("users.refresh-retry-interval").toMillis.millis,
            expirationTolerance = config.getDuration("users.expiration-tolerance").toMillis.millis,
            context = EndpointContext(config.getConfig("users.context"))
          ),
          nodes = Config.Nodes(
            issuer = config.getString("nodes.issuer"),
            audience = config.getString("nodes.audience"),
            identityClaim = config.getString("nodes.identity-claim"),
            jwksEndpoint = config.getString("nodes.jwks-endpoint"),
            refreshInterval = config.getDuration("nodes.refresh-interval").toMillis.millis,
            refreshRetryInterval = config.getDuration("nodes.refresh-retry-interval").toMillis.millis,
            expirationTolerance = config.getDuration("nodes.expiration-tolerance").toMillis.millis,
            context = EndpointContext(config.getConfig("nodes.context"))
          )
        )
    }
  }
}
