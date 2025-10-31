package stasis.server.service.components

import java.util.UUID

import scala.concurrent.duration._

import com.typesafe.config.Config
import io.github.sndnv.layers.events.EventCollector
import io.github.sndnv.layers.security.jwt.DefaultJwtProvider
import io.github.sndnv.layers.security.jwt.JwtProvider
import io.github.sndnv.layers.security.oauth.OAuthClient
import io.github.sndnv.layers.security.tls.EndpointContext
import io.github.sndnv.layers.service.components.Component
import io.github.sndnv.layers.service.components.ComponentLoader
import io.github.sndnv.layers.telemetry.TelemetryContext
import org.apache.pekko.actor.typed.ActorSystem
import org.apache.pekko.util.Timeout

import stasis.core.api.PoolClient
import stasis.core.networking.grpc.GrpcEndpointAddress
import stasis.core.networking.grpc.GrpcEndpointClient
import stasis.core.networking.http.HttpEndpointAddress
import stasis.core.networking.http.HttpEndpointClient
import stasis.core.routing.DefaultRouter
import stasis.core.routing.Node
import stasis.core.routing.NodeProxy
import stasis.core.routing.Router
import stasis.core.security.JwtNodeCredentialsProvider
import stasis.server.routing.ServerRouter
import stasis.server.service.CorePersistence

object RoutingComponentLoader
    extends ComponentLoader.Required[
      Router,
      (DefaultComponentContext, EventCollector, CorePersistence, Authenticators)
    ] {
  override val name: String = "routing"
  override val component: Option[String] = Some("router")

  override protected def default(
    config: Config
  )(implicit
    context: ComponentLoader.Context[(DefaultComponentContext, EventCollector, CorePersistence, Authenticators)]
  ): Component[Router] = {
    implicit val (base, events, corePersistence, authenticators) = context.value
    implicit val (system, timeout, telemetry) = base.components

    new ServerRouterComponent(
      config = ServerRouterComponent.Config(config = config),
      oAuthClient = authenticators.instance,
      corePersistence = corePersistence
    )
  }

  private class ServerRouterComponent(
    config: ServerRouterComponent.Config,
    oAuthClient: OAuthClient,
    corePersistence: CorePersistence
  )(implicit system: ActorSystem[Nothing], timeout: Timeout, telemetry: TelemetryContext, events: EventCollector)
      extends Component[ServerRouter] {
    override def renderConfig(withPrefix: String): String =
      s"""
         |$withPrefix  router-id: ${config.routerId.toString}
         |$withPrefix
         |$withPrefix  clients:
         |$withPrefix    authentication:
         |$withPrefix      token-expiration-tolerance: ${config.clients.authentication.tokenExpirationTolerance.toCoarsest.toString}
         |$withPrefix
         |$withPrefix    core:
         |$withPrefix      max-chunk-size: ${config.clients.core.maxChunkSize.toString}
         |$withPrefix      http:
         |$withPrefix        request-buffer-size: ${config.clients.core.http.requestBufferSize.toString}
         |$withPrefix        retry:
         |$withPrefix          min-backoff:   ${config.clients.core.http.minBackoff.toCoarsest.toString}
         |$withPrefix          max-backoff:   ${config.clients.core.http.maxBackoff.toCoarsest.toString}
         |$withPrefix          random-factor: ${config.clients.core.http.randomFactor.toString}
         |$withPrefix          max-retries:   ${config.clients.core.http.maxRetries.toString}""".stripMargin

    override val component: ServerRouter = {
      import system.executionContext

      val clientJwtProvider: JwtProvider = DefaultJwtProvider(
        client = oAuthClient,
        clientParameters = OAuthClient.GrantParameters.ClientCredentials(),
        expirationTolerance = config.clients.authentication.tokenExpirationTolerance
      )

      val coreHttpEndpointClient = HttpEndpointClient(
        credentials = JwtNodeCredentialsProvider[HttpEndpointAddress](
          nodeStore = corePersistence.nodes.view,
          underlying = clientJwtProvider
        ),
        context = config.clients.core.endpointContext,
        maxChunkSize = config.clients.core.maxChunkSize,
        config = config.clients.core.http
      )

      val coreGrpcEndpointClient = GrpcEndpointClient(
        credentials = JwtNodeCredentialsProvider[GrpcEndpointAddress](
          nodeStore = corePersistence.nodes.view,
          underlying = clientJwtProvider
        ),
        context = config.clients.core.endpointContext,
        maxChunkSize = config.clients.core.maxChunkSize
      )

      val nodeProxy = NodeProxy(
        httpClient = coreHttpEndpointClient,
        grpcClient = coreGrpcEndpointClient
      )

      ServerRouter(
        underlying = DefaultRouter(
          routerId = config.routerId,
          persistence = DefaultRouter.Persistence(
            manifests = corePersistence.manifests,
            nodes = corePersistence.nodes.view,
            reservations = corePersistence.reservations,
            staging = corePersistence.staging
          ),
          nodeProxy = nodeProxy
        )
      )
    }
  }

  private object ServerRouterComponent {
    final case class Config(
      routerId: Node.Id,
      clients: Config.Clients
    )

    object Config {
      final case class Clients(
        authentication: Clients.Authentication,
        core: Clients.Core
      )

      object Clients {
        final case class Authentication(
          tokenExpirationTolerance: FiniteDuration
        )

        final case class Core(
          maxChunkSize: Int,
          http: PoolClient.Config,
          endpointContext: Option[EndpointContext]
        )
      }

      def apply(config: com.typesafe.config.Config): Config =
        Config(
          routerId = UUID.fromString(config.getString("default.id")),
          clients = Config.Clients(
            authentication = Config.Clients.Authentication(
              tokenExpirationTolerance = config
                .getDuration("default.clients.authentication.token-expiration-tolerance")
                .toMillis
                .millis
            ),
            core = Config.Clients.Core(
              maxChunkSize = config.getInt("default.clients.core.max-chunk-size"),
              http = PoolClient.Config(
                minBackoff = config.getDuration("default.clients.core.http.retry.min-backoff").toMillis.millis,
                maxBackoff = config.getDuration("default.clients.core.http.retry.max-backoff").toMillis.millis,
                randomFactor = config.getDouble("default.clients.core.http.retry.random-factor"),
                maxRetries = config.getInt("default.clients.core.http.retry.max-retries"),
                requestBufferSize = config.getInt("default.clients.core.http.request-buffer-size")
              ),
              endpointContext = EndpointContext(config.getConfig("default.clients.core.context"))
            )
          )
        )
    }
  }
}
