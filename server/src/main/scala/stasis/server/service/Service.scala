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
import stasis.core.security.jwt.{DefaultJwtAuthenticator, DefaultJwtProvider, JwtProvider}
import stasis.core.security.keys.RemoteKeyProvider
import stasis.core.security.tls.EndpointContext
import stasis.core.security.{JwtNodeAuthenticator, JwtNodeCredentialsProvider, NodeAuthenticator}
import stasis.core.security.oauth.DefaultOAuthClient
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

    val instanceAuthenticatorConfig = Config.InstanceAuthenticatorConfig(rawConfig.getConfig("authenticators.instance"))
    val serverId = UUID.fromString(instanceAuthenticatorConfig.clientId)

    val authenticationEndpointContext: Option[HttpsConnectionContext] =
      EndpointContext.fromConfig(rawConfig.getConfig("clients.authentication.context"))

    val jwtProvider: JwtProvider = new DefaultJwtProvider(
      client = new DefaultOAuthClient(
        tokenEndpoint = instanceAuthenticatorConfig.tokenEndpoint,
        client = serverId.toString,
        clientSecret = instanceAuthenticatorConfig.clientSecret,
        useQueryString = instanceAuthenticatorConfig.useQueryString,
        context = authenticationEndpointContext
      ),
      expirationTolerance = instanceAuthenticatorConfig.expirationTolerance
    )

    val persistenceConfig = rawConfig.getConfig("persistence")

    val serverPersistence: ServerPersistence = new ServerPersistence(persistenceConfig)
    val corePersistence: CorePersistence = new CorePersistence(persistenceConfig)

    val resourceProvider: ResourceProvider = new DefaultResourceProvider(
      resources = serverPersistence.resources ++ corePersistence.resources,
      users = serverPersistence.users.view()
    )

    val userAuthenticatorConfig = Config.UserAuthenticatorConfig(rawConfig.getConfig("authenticators.users"))
    val userAuthenticator: UserAuthenticator = new DefaultUserAuthenticator(
      store = serverPersistence.users.view(),
      underlying = new DefaultJwtAuthenticator(
        provider = RemoteKeyProvider(
          jwksEndpoint = userAuthenticatorConfig.jwksEndpoint,
          context = authenticationEndpointContext,
          refreshInterval = userAuthenticatorConfig.refreshInterval,
          refreshRetryInterval = userAuthenticatorConfig.refreshRetryInterval,
          issuer = userAuthenticatorConfig.issuer
        ),
        audience = userAuthenticatorConfig.audience,
        identityClaim = userAuthenticatorConfig.identityClaim,
        expirationTolerance = userAuthenticatorConfig.expirationTolerance
      )
    )

    val nodeAuthenticatorConfig = Config.NodeAuthenticatorConfig(rawConfig.getConfig("authenticators.nodes"))
    val nodeAuthenticator: NodeAuthenticator[HttpCredentials] = new JwtNodeAuthenticator(
      nodeStore = corePersistence.nodes.view,
      underlying = new DefaultJwtAuthenticator(
        provider = RemoteKeyProvider(
          jwksEndpoint = nodeAuthenticatorConfig.jwksEndpoint,
          context = authenticationEndpointContext,
          refreshInterval = nodeAuthenticatorConfig.refreshInterval,
          refreshRetryInterval = nodeAuthenticatorConfig.refreshRetryInterval,
          issuer = nodeAuthenticatorConfig.issuer
        ),
        audience = nodeAuthenticatorConfig.audience,
        identityClaim = nodeAuthenticatorConfig.identityClaim,
        expirationTolerance = nodeAuthenticatorConfig.expirationTolerance
      )
    )

    val clientEndpointContext: Option[HttpsConnectionContext] =
      EndpointContext.fromConfig(rawConfig.getConfig("clients.core.context"))

    val coreHttpEndpointClient = new HttpEndpointClient(
      credentials = new JwtNodeCredentialsProvider[HttpEndpointAddress](
        nodeStore = corePersistence.nodes.view,
        underlying = jwtProvider
      ),
      context = clientEndpointContext,
      requestBufferSize = rawConfig.getInt("clients.core.request-buffer-size")
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

    log.info(
      s"""
         |Config(
         |  bootstrap:
         |    enabled: ${rawConfig.getBoolean("bootstrap.enabled").toString}
         |    config:  ${rawConfig.getString("bootstrap.config")}
         |
         |  service:
         |    iqt:  ${timeout.duration.toMillis.toString} ms
         |
         |    api:
         |      interface:  ${apiConfig.interface}
         |      port:       ${apiConfig.port.toString}
         |      context:
         |        protocol: ${apiConfig.context.protocol}
         |        keystore: ${apiConfig.context.keyStoreConfig.map(_.storePath).getOrElse("none")}
         |
         |    core:
         |      interface:  ${coreConfig.interface}
         |      port:       ${coreConfig.port.toString}
         |      context:
         |        protocol: ${coreConfig.context.protocol}
         |        keystore: ${coreConfig.context.keyStoreConfig.map(_.storePath).getOrElse("none")}
         |
         |  authenticators:
         |    users:
         |      issuer:                 ${userAuthenticatorConfig.issuer}
         |      audience:               ${userAuthenticatorConfig.audience}
         |      identity-claim:         ${userAuthenticatorConfig.identityClaim}
         |      jwks-endpoint:          ${userAuthenticatorConfig.jwksEndpoint}
         |      refresh-interval:       ${userAuthenticatorConfig.refreshInterval.toSeconds.toString} s
         |      refresh-retry-interval: ${userAuthenticatorConfig.refreshRetryInterval.toMillis.toString} ms
         |      expiration-tolerance:   ${userAuthenticatorConfig.expirationTolerance.toMillis.toString} ms
         |
         |    nodes:
         |      issuer:                 ${nodeAuthenticatorConfig.issuer}
         |      audience:               ${nodeAuthenticatorConfig.audience}
         |      identity-claim:         ${nodeAuthenticatorConfig.identityClaim}
         |      jwks-endpoint:          ${nodeAuthenticatorConfig.jwksEndpoint}
         |      refresh-interval:       ${nodeAuthenticatorConfig.refreshInterval.toSeconds.toString} s
         |      refresh-retry-interval: ${nodeAuthenticatorConfig.refreshRetryInterval.toMillis.toString} ms
         |      expiration-tolerance:   ${nodeAuthenticatorConfig.expirationTolerance.toMillis.toString} ms
         |
         |    instance:
         |      token-endpoint:         ${instanceAuthenticatorConfig.tokenEndpoint}
         |      client-id:              ${instanceAuthenticatorConfig.clientId}
         |      expiration-tolerance:   ${instanceAuthenticatorConfig.expirationTolerance.toMillis.toString} ms
         |      use-query-string:       ${instanceAuthenticatorConfig.useQueryString.toString}
         |
         |  persistence:
         |    database:
         |      core:
         |        profile:    ${corePersistence.profile.getClass.getSimpleName}
         |        url:        ${corePersistence.databaseUrl}
         |        driver:     ${corePersistence.databaseDriver}
         |        keep-alive: ${corePersistence.databaseKeepAlive.toString}
         |
         |      server:
         |        profile:    ${serverPersistence.profile.getClass.getSimpleName}
         |        url:        ${serverPersistence.databaseUrl}
         |        driver:     ${serverPersistence.databaseDriver}
         |        keep-alive: ${serverPersistence.databaseKeepAlive.toString}
         |
         |    users:
         |      salt-size: ${serverPersistence.userSaltSize.toString}
         |
         |    reservations:
         |      expiration: ${corePersistence.reservationExpiration.toMillis.toString} ms
         |
         |    nodes:
         |      caching-enabled: ${corePersistence.nodeCachingEnabled.toString}
         |
         |    staging:
         |      enabled:         ${corePersistence.stagingStoreDescriptor.isDefined}
         |      destaging-delay: ${corePersistence.stagingStoreDestagingDelay.toMillis.toString} ms
         |      store:           ${corePersistence.stagingStoreDescriptor.map(_.toString).getOrElse("none")}
         |)
       """.stripMargin
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

    final case class UserAuthenticatorConfig(
      issuer: String,
      audience: String,
      identityClaim: String,
      jwksEndpoint: String,
      refreshInterval: FiniteDuration,
      refreshRetryInterval: FiniteDuration,
      expirationTolerance: FiniteDuration
    )

    object UserAuthenticatorConfig {
      def apply(config: com.typesafe.config.Config): UserAuthenticatorConfig =
        UserAuthenticatorConfig(
          issuer = config.getString("issuer"),
          audience = config.getString("audience"),
          identityClaim = config.getString("identity-claim"),
          jwksEndpoint = config.getString("jwks-endpoint"),
          refreshInterval = config.getDuration("refresh-interval").toMillis.millis,
          refreshRetryInterval = config.getDuration("refresh-retry-interval").toMillis.millis,
          expirationTolerance = config.getDuration("expiration-tolerance").toMillis.millis
        )
    }

    final case class NodeAuthenticatorConfig(
      issuer: String,
      audience: String,
      identityClaim: String,
      jwksEndpoint: String,
      refreshInterval: FiniteDuration,
      refreshRetryInterval: FiniteDuration,
      expirationTolerance: FiniteDuration
    )

    object NodeAuthenticatorConfig {
      def apply(config: com.typesafe.config.Config): NodeAuthenticatorConfig =
        NodeAuthenticatorConfig(
          issuer = config.getString("issuer"),
          audience = config.getString("audience"),
          identityClaim = config.getString("identity-claim"),
          jwksEndpoint = config.getString("jwks-endpoint"),
          refreshInterval = config.getDuration("refresh-interval").toMillis.millis,
          refreshRetryInterval = config.getDuration("refresh-retry-interval").toMillis.millis,
          expirationTolerance = config.getDuration("expiration-tolerance").toMillis.millis
        )
    }

    final case class InstanceAuthenticatorConfig(
      tokenEndpoint: String,
      clientId: String,
      clientSecret: String,
      expirationTolerance: FiniteDuration,
      useQueryString: Boolean
    )

    object InstanceAuthenticatorConfig {
      def apply(config: com.typesafe.config.Config): InstanceAuthenticatorConfig =
        InstanceAuthenticatorConfig(
          tokenEndpoint = config.getString("token-endpoint"),
          clientId = config.getString("client-id"),
          clientSecret = config.getString("client-secret"),
          expirationTolerance = config.getDuration("expiration-tolerance").toMillis.millis,
          useQueryString = config.getBoolean("use-query-string")
        )
    }
  }
}
