package stasis.server.service

import java.util.UUID
import java.util.concurrent.atomic.AtomicReference

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.scaladsl.adapter._
import akka.actor.typed.{ActorSystem, Behavior, SpawnProtocol}
import akka.event.{Logging, LoggingAdapter}
import akka.http.scaladsl.model.headers.HttpCredentials
import akka.http.scaladsl.{ConnectionContext, HttpsConnectionContext}
import akka.util.Timeout
import com.typesafe.{config => typesafe}
import stasis.core.networking.grpc.{GrpcEndpointAddress, GrpcEndpointClient}
import stasis.core.networking.http.{HttpEndpoint, HttpEndpointAddress, HttpEndpointClient}
import stasis.core.routing.{DefaultRouter, NodeProxy, Router}
import stasis.core.security.jwt.{JwtAuthenticator, JwtProvider}
import stasis.core.security.keys.RemoteKeyProvider
import stasis.core.security.tls.EndpointContext
import stasis.core.security.{JwtNodeAuthenticator, JwtNodeCredentialsProvider, NodeAuthenticator}
import stasis.server.api.ApiEndpoint
import stasis.server.security._

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._
import scala.util.{Failure, Success, Try}

trait Service {
  import Service._

  private val serviceState: AtomicReference[State] = new AtomicReference[State](State.Starting)

  private implicit val system: ActorSystem[SpawnProtocol] = ActorSystem(
    Behaviors.setup(_ => SpawnProtocol.behavior): Behavior[SpawnProtocol],
    name = "stasis-server-service"
  )

  private implicit val untyped: akka.actor.ActorSystem = system.toUntyped
  private implicit val ec: ExecutionContext = system.executionContext
  private implicit val log: LoggingAdapter = Logging(untyped, this.getClass.getName)

  protected def systemConfig: typesafe.Config = system.settings.config

  private val rawConfig: typesafe.Config = systemConfig.getConfig("stasis.server")
  private val apiConfig: Config = Config(rawConfig.getConfig("service.api"))
  private val coreConfig: Config = Config(rawConfig.getConfig("service.core"))

  Try {
    implicit val timeout: Timeout = rawConfig.getDuration("service.internal-query-timeout").toMillis.millis

    val instanceAuthenticatorConfig = rawConfig.getConfig("authenticators.instance")
    val serverId = UUID.fromString(instanceAuthenticatorConfig.getString("client-id"))

    val authenticationEndpointContext: Option[HttpsConnectionContext] =
      if (rawConfig.getBoolean("clients.authentication.context.enabled")) {
        Some(EndpointContext.fromConfig(rawConfig.getConfig("clients.authentication.context")))
      } else {
        None
      }

    val jwtProvider: JwtProvider = new JwtProvider(
      tokenEndpoint = instanceAuthenticatorConfig.getString("token-endpoint"),
      client = serverId.toString,
      clientSecret = instanceAuthenticatorConfig.getString("client-secret"),
      expirationTolerance = instanceAuthenticatorConfig.getDuration("expiration-tolerance").toMillis.millis,
      context = authenticationEndpointContext
    )

    val persistenceConfig = rawConfig.getConfig("persistence")

    val serverPersistence: ServerPersistence = new ServerPersistence(persistenceConfig)
    val corePersistence: CorePersistence = new CorePersistence(persistenceConfig)

    val resourceProvider: ResourceProvider = new DefaultResourceProvider(
      resources = serverPersistence.resources ++ corePersistence.resources,
      users = serverPersistence.users.view()
    )

    val userAuthenticatorConfig = rawConfig.getConfig("authenticators.users")
    val userAuthenticator: UserAuthenticator = new DefaultUserAuthenticator(
      store = serverPersistence.users.view(),
      underlying = new JwtAuthenticator(
        provider = RemoteKeyProvider(
          jwksEndpoint = userAuthenticatorConfig.getString("jwks-endpoint"),
          context = authenticationEndpointContext,
          refreshInterval = userAuthenticatorConfig.getDuration("refresh-interval").toSeconds.seconds,
          issuer = userAuthenticatorConfig.getString("issuer")
        ),
        audience = userAuthenticatorConfig.getString("audience"),
        identityClaim = userAuthenticatorConfig.getString("identity-claim"),
        expirationTolerance = userAuthenticatorConfig.getDuration("expiration-tolerance").toMillis.millis
      )
    )

    val nodeAuthenticatorConfig = rawConfig.getConfig("authenticators.nodes")
    val nodeAuthenticator: NodeAuthenticator[HttpCredentials] = new JwtNodeAuthenticator(
      nodeStore = corePersistence.nodes.view,
      underlying = new JwtAuthenticator(
        provider = RemoteKeyProvider(
          jwksEndpoint = nodeAuthenticatorConfig.getString("jwks-endpoint"),
          context = authenticationEndpointContext,
          refreshInterval = nodeAuthenticatorConfig.getDuration("refresh-interval").toSeconds.seconds,
          issuer = nodeAuthenticatorConfig.getString("issuer")
        ),
        audience = nodeAuthenticatorConfig.getString("audience"),
        identityClaim = nodeAuthenticatorConfig.getString("identity-claim"),
        expirationTolerance = nodeAuthenticatorConfig.getDuration("expiration-tolerance").toMillis.millis
      )
    )

    val clientEndpointContext: Option[HttpsConnectionContext] =
      if (rawConfig.getBoolean("clients.core.context.enabled")) {
        Some(EndpointContext.fromConfig(rawConfig.getConfig("clients.core.context")))
      } else {
        None
      }

    val coreHttpEndpointClient = new HttpEndpointClient(
      credentials = new JwtNodeCredentialsProvider[HttpEndpointAddress](
        nodeStore = corePersistence.nodes.view,
        underlying = jwtProvider
      ),
      context = clientEndpointContext
    )

    val coreGrpcEndpointClient = new GrpcEndpointClient(
      credentials = new JwtNodeCredentialsProvider[GrpcEndpointAddress](
        nodeStore = corePersistence.nodes.view,
        underlying = jwtProvider
      ),
      context = clientEndpointContext
    )

    val nodeProxy = new NodeProxy(
      httpClient = coreHttpEndpointClient,
      grpcClient = coreGrpcEndpointClient
    )

    val router: Router = new DefaultRouter(
      routerId = serverId,
      persistence = DefaultRouter.Persistence(
        manifests = corePersistence.manifests,
        nodes = corePersistence.nodes.view,
        reservations = corePersistence.reservations,
        staging = corePersistence.staging,
      ),
      nodeProxy = nodeProxy
    )

    val apiServices = ApiServices(
      persistence = serverPersistence,
      endpoint = new ApiEndpoint(
        resourceProvider = resourceProvider,
        authenticator = userAuthenticator
      ),
      context = EndpointContext.create(apiConfig.context)
    )

    val coreServices = CoreServices(
      persistence = corePersistence,
      endpoint = new HttpEndpoint(
        router = router,
        reservationStore = corePersistence.reservations.view,
        authenticator = nodeAuthenticator
      ),
      context = EndpointContext.create(coreConfig.context)
    )

    (apiServices, coreServices)
  } match {
    case Success((apiServices: ApiServices, coreServices: CoreServices)) =>
      Bootstrap
        .run(rawConfig.getConfig("bootstrap"), apiServices.persistence, coreServices.persistence)
        .onComplete {
          case Success(_) =>
            log.info("Service API starting on [{}:{}]...", apiConfig.interface, apiConfig.port)

            val api = apiServices.endpoint.start(
              interface = apiConfig.interface,
              port = apiConfig.port,
              context = apiServices.context
            )

            log.info("Service core starting on [{}:{}]...", coreConfig.interface, coreConfig.port)

            val core = coreServices.endpoint.start(
              interface = coreConfig.interface,
              port = coreConfig.port,
              context = coreServices.context
            )

            serviceState.set(State.Started(apiServices, coreServices))

          case Failure(e) =>
            log.error(e, "Bootstrap failed: [{}]", e.getMessage)
            serviceState.set(State.BootstrapFailed(e))
            stop()
        }

    case Failure(e) =>
      log.error(e, "Service startup failed: [{}]", e.getMessage)
      serviceState.set(State.StartupFailed(e))
      stop()
  }

  private val _ = sys.addShutdownHook(stop())

  def stop(): Unit = {
    log.info("Service stopping...")
    val _ = system.terminate()
  }

  def state: State = serviceState.get()
}

object Service {
  final case class ApiServices(
    persistence: ServerPersistence,
    endpoint: ApiEndpoint,
    context: ConnectionContext
  )

  final case class CoreServices(
    persistence: CorePersistence,
    endpoint: HttpEndpoint,
    context: ConnectionContext
  )

  sealed trait State
  object State {
    case object Starting extends State

    final case class Started(
      apiServices: ApiServices,
      coreServices: CoreServices
    ) extends State

    final case class BootstrapFailed(throwable: Throwable) extends State

    final case class StartupFailed(throwable: Throwable) extends State
  }

  final case class Config(
    interface: String,
    port: Int,
    context: EndpointContext.ContextConfig
  )

  object Config {
    def apply(config: com.typesafe.config.Config): Config =
      Config(
        interface = config.getString("interface"),
        port = config.getInt("port"),
        context = EndpointContext.ContextConfig(config.getConfig("context"))
      )
  }
}
