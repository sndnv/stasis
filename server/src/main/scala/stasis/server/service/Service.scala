package stasis.server.service

import java.time.Instant
import java.util.UUID
import java.util.concurrent.atomic.AtomicReference

import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.Failure
import scala.util.Success
import scala.util.Try

import com.typesafe.{config => typesafe}
import io.prometheus.client.hotspot.DefaultExports
import org.apache.pekko.actor.typed.ActorSystem
import org.apache.pekko.actor.typed.scaladsl.Behaviors
import org.apache.pekko.http.scaladsl.model.headers.HttpCredentials
import org.apache.pekko.util.Timeout
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import play.api.libs.json.JsObject
import play.api.libs.json.Json

import stasis.core
import stasis.core.api.PoolClient
import stasis.core.discovery.providers.server.ServiceDiscoveryProvider
import stasis.core.networking.grpc.GrpcEndpointAddress
import stasis.core.networking.grpc.GrpcEndpointClient
import stasis.core.networking.http.HttpEndpoint
import stasis.core.networking.http.HttpEndpointAddress
import stasis.core.networking.http.HttpEndpointClient
import stasis.core.routing.DefaultRouter
import stasis.core.routing.NodeProxy
import stasis.core.routing.Router
import stasis.core.security.JwtNodeAuthenticator
import stasis.core.security.JwtNodeCredentialsProvider
import stasis.core.security.NodeAuthenticator
import stasis.layers
import stasis.layers.security.jwt.DefaultJwtAuthenticator
import stasis.layers.security.jwt.DefaultJwtProvider
import stasis.layers.security.jwt.JwtProvider
import stasis.layers.security.keys.RemoteKeyProvider
import stasis.layers.security.oauth.DefaultOAuthClient
import stasis.layers.security.oauth.OAuthClient
import stasis.layers.security.tls.EndpointContext
import stasis.layers.service.BootstrapProvider
import stasis.layers.telemetry.DefaultTelemetryContext
import stasis.layers.telemetry.TelemetryContext
import stasis.layers.telemetry.analytics.AnalyticsCollector
import stasis.layers.telemetry.metrics.MetricsExporter
import stasis.server.BuildInfo
import stasis.server.api.ApiEndpoint
import stasis.server.api.BootstrapEndpoint
import stasis.server.api.routes.DeviceBootstrap
import stasis.server.security._
import stasis.server.security.authenticators._
import stasis.server.security.devices._
import stasis.server.security.users.IdentityUserCredentialsManager
import stasis.server.security.users.UserCredentialsManager
import stasis.server.service.Service.Config.BootstrapApiConfig
import stasis.server.service.bootstrap.DatasetDefinitionBootstrapEntityProvider
import stasis.server.service.bootstrap.DeviceBootstrapEntityProvider
import stasis.server.service.bootstrap.NodeBootstrapEntityProvider
import stasis.server.service.bootstrap.ScheduleBootstrapEntityProvider
import stasis.server.service.bootstrap.UserBootstrapEntityProvider
import stasis.shared.model.devices.DeviceBootstrapParameters
import stasis.shared.secrets.SecretsConfig

trait Service {
  import Service._

  private val serviceState: AtomicReference[State] = new AtomicReference[State](State.Starting)

  private implicit val system: ActorSystem[Nothing] = ActorSystem(
    guardianBehavior = Behaviors.ignore,
    name = "stasis-server-service"
  )

  protected def systemConfig: typesafe.Config = system.settings.config

  private val rawConfig: typesafe.Config = systemConfig.getConfig("stasis.server")
  private val apiConfig: Config = Config(rawConfig.getConfig("service.api"))
  private val coreConfig: Config = Config(rawConfig.getConfig("service.core"))
  private val metricsConfig: Config = Config(rawConfig.getConfig("service.telemetry.metrics"))
  private val bootstrapApiConfig: BootstrapApiConfig = BootstrapApiConfig(rawConfig.getConfig("bootstrap.api"))

  private implicit val ec: ExecutionContext = system.executionContext
  private implicit val log: Logger = LoggerFactory.getLogger(this.getClass.getName)
  private val exporter: MetricsExporter = Telemetry.createMetricsExporter(config = metricsConfig)

  Try {
    implicit val timeout: Timeout = rawConfig.getDuration("service.internal-query-timeout").toMillis.millis

    val instanceAuthenticatorConfig = Config.InstanceAuthenticator(rawConfig.getConfig("authenticators.instance"))

    val authenticationEndpointContext: Option[EndpointContext] =
      EndpointContext(rawConfig.getConfig("clients.authentication.context"))

    implicit val telemetry: TelemetryContext = DefaultTelemetryContext(
      metricsProviders = Set(
        layers.security.Metrics.default(meter = exporter.meter, namespace = Telemetry.Instrumentation),
        layers.api.Metrics.default(meter = exporter.meter, namespace = Telemetry.Instrumentation),
        layers.persistence.Metrics.default(meter = exporter.meter, namespace = Telemetry.Instrumentation),
        core.persistence.Metrics.default(meter = exporter.meter, namespace = Telemetry.Instrumentation),
        core.routing.Metrics.default(meter = exporter.meter, namespace = Telemetry.Instrumentation)
      ).flatten,
      analyticsCollector = AnalyticsCollector.NoOp
    )

    val oauthClient = DefaultOAuthClient(
      tokenEndpoint = instanceAuthenticatorConfig.tokenEndpoint,
      client = instanceAuthenticatorConfig.clientId,
      clientSecret = instanceAuthenticatorConfig.clientSecret,
      useQueryString = instanceAuthenticatorConfig.useQueryString,
      context = authenticationEndpointContext
    )

    val clientJwtProvider: JwtProvider = DefaultJwtProvider(
      client = oauthClient,
      clientParameters = OAuthClient.GrantParameters.ClientCredentials(),
      expirationTolerance = instanceAuthenticatorConfig.expirationTolerance
    )

    val identityCredentialsManagerConfig = Config.IdentityCredentialsManager(
      config = rawConfig.getConfig("credentials-managers.identity")
    )

    val identityCredentialsManagerJwtProvider: JwtProvider = DefaultJwtProvider(
      client = oauthClient,
      clientParameters = OAuthClient.GrantParameters.ResourceOwnerPasswordCredentials(
        username = identityCredentialsManagerConfig.managementUser,
        password = identityCredentialsManagerConfig.managementUserPassword
      ),
      expirationTolerance = instanceAuthenticatorConfig.expirationTolerance
    )

    val persistenceConfig = rawConfig.getConfig("persistence")

    val serverPersistence: ServerPersistence = ServerPersistence(persistenceConfig)
    val corePersistence: CorePersistence = CorePersistence(persistenceConfig)

    val resourceProvider: ResourceProvider = DefaultResourceProvider(
      resources = serverPersistence.resources ++ corePersistence.resources,
      users = serverPersistence.users.view()
    )

    val userAuthenticatorConfig = Config.UserAuthenticator(rawConfig.getConfig("authenticators.users"))
    val userAuthenticator: UserAuthenticator = DefaultUserAuthenticator(
      store = serverPersistence.users.view(),
      underlying = DefaultJwtAuthenticator(
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

    val userCredentialsManager: UserCredentialsManager = IdentityUserCredentialsManager(
      identityUrl = identityCredentialsManagerConfig.url,
      identityCredentials = CredentialsProvider.Default(
        scope = identityCredentialsManagerConfig.managementUserScope,
        underlying = identityCredentialsManagerJwtProvider
      ),
      context = EndpointContext(identityCredentialsManagerConfig.contextConfig)
    )

    val deviceBootstrapConfig = Config.DeviceBootstrap.config(rawConfig.getConfig("bootstrap.devices"))
    val deviceBootstrapParams = Config.DeviceBootstrap.params(rawConfig.getConfig("bootstrap.devices.parameters"))

    val bootstrapEndpoint = if (bootstrapApiConfig.enabled) {
      val bootstrapCodeAuthenticator: BootstrapCodeAuthenticator = DefaultBootstrapCodeAuthenticator(
        store = serverPersistence.deviceBootstrapCodes.manage()
      )

      val deviceBootstrapCodeGenerator: DeviceBootstrapCodeGenerator = DeviceBootstrapCodeGenerator(
        codeSize = deviceBootstrapConfig.codeSize,
        expiration = deviceBootstrapConfig.codeExpiration
      )

      val deviceClientSecretGenerator: DeviceClientSecretGenerator = DeviceClientSecretGenerator(
        secretSize = deviceBootstrapConfig.secretSize
      )

      val deviceCredentialsManager: DeviceCredentialsManager = IdentityDeviceCredentialsManager(
        identityUrl = identityCredentialsManagerConfig.url,
        identityCredentials = CredentialsProvider.Default(
          scope = identityCredentialsManagerConfig.managementUserScope,
          underlying = identityCredentialsManagerJwtProvider
        ),
        redirectUri = deviceBootstrapConfig.credentialsManager.clientRedirectUri,
        tokenExpiration = deviceBootstrapConfig.credentialsManager.clientTokenExpiration,
        context = EndpointContext(identityCredentialsManagerConfig.contextConfig)
      )

      Some(
        BootstrapEndpoint(
          resourceProvider = resourceProvider,
          userAuthenticator = userAuthenticator,
          bootstrapCodeAuthenticator = bootstrapCodeAuthenticator,
          deviceBootstrapContext = DeviceBootstrap.BootstrapContext(
            bootstrapCodeGenerator = deviceBootstrapCodeGenerator,
            clientSecretGenerator = deviceClientSecretGenerator,
            credentialsManager = deviceCredentialsManager,
            deviceParams = deviceBootstrapParams
          )
        )
      )
    } else {
      None
    }

    val nodeAuthenticatorConfig = Config.NodeAuthenticator(rawConfig.getConfig("authenticators.nodes"))
    val nodeAuthenticator: NodeAuthenticator[HttpCredentials] = JwtNodeAuthenticator(
      nodeStore = corePersistence.nodes.view,
      underlying = DefaultJwtAuthenticator(
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

    val clientEndpointContext: Option[EndpointContext] =
      EndpointContext(rawConfig.getConfig("clients.core.context"))

    val coreHttpEndpointClient = HttpEndpointClient(
      credentials = JwtNodeCredentialsProvider[HttpEndpointAddress](
        nodeStore = corePersistence.nodes.view,
        underlying = clientJwtProvider
      ),
      context = clientEndpointContext,
      maxChunkSize = rawConfig.getInt("clients.core.max-chunk-size"),
      config = PoolClient.Config(
        minBackoff = rawConfig.getDuration("clients.core.retry.min-backoff").toMillis.millis,
        maxBackoff = rawConfig.getDuration("clients.core.retry.max-backoff").toMillis.millis,
        randomFactor = rawConfig.getDouble("clients.core.retry.random-factor"),
        maxRetries = rawConfig.getInt("clients.core.retry.max-retries"),
        requestBufferSize = rawConfig.getInt("clients.core.request-buffer-size")
      )
    )

    val coreGrpcEndpointClient = GrpcEndpointClient(
      credentials = JwtNodeCredentialsProvider[GrpcEndpointAddress](
        nodeStore = corePersistence.nodes.view,
        underlying = clientJwtProvider
      ),
      context = clientEndpointContext,
      maxChunkSize = rawConfig.getInt("clients.core.max-chunk-size")
    )

    val nodeProxy = NodeProxy(
      httpClient = coreHttpEndpointClient,
      grpcClient = coreGrpcEndpointClient
    )

    val routingConfig = rawConfig.getConfig("routing")
    val routerId = UUID.fromString(routingConfig.getString("default.router-id"))

    val router: Router = DefaultRouter(
      routerId = routerId,
      persistence = DefaultRouter.Persistence(
        manifests = corePersistence.manifests,
        nodes = corePersistence.nodes.view,
        reservations = corePersistence.reservations,
        staging = corePersistence.staging
      ),
      nodeProxy = nodeProxy
    )

    val serviceDiscoveryProvider = ServiceDiscoveryProvider(config = rawConfig.getConfig("service.discovery"))

    val apiServices = ApiServices(
      persistence = serverPersistence,
      apiEndpoint = ApiEndpoint(
        resourceProvider = resourceProvider,
        authenticator = userAuthenticator,
        userCredentialsManager = userCredentialsManager,
        serviceDiscoveryProvider = serviceDiscoveryProvider,
        secretsConfig = deviceBootstrapParams.secrets
      ),
      bootstrapEndpoint = bootstrapEndpoint,
      context = apiConfig.context
    )

    val coreServices = CoreServices(
      persistence = corePersistence,
      endpoint = HttpEndpoint(
        router = router,
        reservationStore = corePersistence.reservations.view,
        authenticator = nodeAuthenticator
      ),
      context = coreConfig.context
    )

    log.info(
      s"""
         |Build(
         |  name:    ${BuildInfo.name}
         |  version: ${BuildInfo.version}
         |  time:    ${Instant.ofEpochMilli(BuildInfo.buildTime).toString}
         |)""".stripMargin
    )

    log.info(
      s"""
         |Config(
         |  service:
         |    iqt:  ${timeout.duration.toMillis.toString} ms
         |
         |    api:
         |      interface:  ${apiConfig.interface}
         |      port:       ${apiConfig.port.toString}
         |      context:
         |        enabled:  ${apiConfig.context.nonEmpty.toString}
         |        protocol: ${apiConfig.context.map(_.config.protocol).getOrElse("none")}
         |        keystore: ${apiConfig.context.flatMap(_.config.keyStoreConfig).map(_.storePath).getOrElse("none")}
         |
         |    core:
         |      interface:  ${coreConfig.interface}
         |      port:       ${coreConfig.port.toString}
         |      context:
         |        enabled:  ${coreConfig.context.nonEmpty.toString}
         |        protocol: ${coreConfig.context.map(_.config.protocol).getOrElse("none")}
         |        keystore: ${coreConfig.context.flatMap(_.config.keyStoreConfig).map(_.storePath).getOrElse("none")}
         |
         |    bootstrap:
         |      mode:   ${rawConfig.getString("service.bootstrap.mode")}
         |      config: ${rawConfig.getString("service.bootstrap.config")}
         |
         |    discovery:
         |      type:   ${rawConfig.getString("service.discovery.type")}
         |      static:
         |        config: ${rawConfig.getString("service.discovery.static.config")}
         |
         |    telemetry:
         |      metrics:
         |        namespace: ${Telemetry.Instrumentation}
         |        interface: ${metricsConfig.interface}
         |        port:      ${metricsConfig.port.toString}
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
         |  credentials-managers:
         |    identity:
         |      url:                  ${identityCredentialsManagerConfig.url}
         |      management:
         |        user:               ${identityCredentialsManagerConfig.managementUser}
         |        password-provided:  ${identityCredentialsManagerConfig.managementUserPassword.nonEmpty.toString}
         |        scope:              ${identityCredentialsManagerConfig.managementUserScope}
         |
         |  routing:
         |    default:
         |      router-id: ${routerId.toString}
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
         |    staging:
         |      enabled:         ${corePersistence.stagingStoreDescriptor.isDefined.toString}
         |      destaging-delay: ${corePersistence.stagingStoreDestagingDelay.toMillis.toString} ms
         |      store:           ${corePersistence.stagingStoreDescriptor.map(_.toString).getOrElse("none")}
         |
         |  bootstrap:
         |    enabled:   ${bootstrapApiConfig.enabled.toString}
         |    interface: ${bootstrapApiConfig.interface}
         |    port:      ${bootstrapApiConfig.port.toString}
         |    context:
         |      enabled:  ${bootstrapApiConfig.context.nonEmpty.toString}
         |      protocol: ${bootstrapApiConfig.context.map(_.config.protocol).getOrElse("none")}
         |      keystore: ${bootstrapApiConfig.context.flatMap(_.config.keyStoreConfig).map(_.storePath).getOrElse("none")}
         |
         |    devices:
         |      code-size:              ${deviceBootstrapConfig.codeSize.toString}
         |      code-expiration:        ${deviceBootstrapConfig.codeExpiration.toMillis.toString} ms
         |      secret-size:            ${deviceBootstrapConfig.secretSize.toString}
         |      credentials-manager:
         |        client:
         |          redirect-uri:       ${deviceBootstrapConfig.credentialsManager.clientRedirectUri}
         |          token-expiration:   ${deviceBootstrapConfig.credentialsManager.clientTokenExpiration.toSeconds.toString} s
         |      parameters:
         |        authentication:
         |          token-endpoint:     ${deviceBootstrapParams.authentication.tokenEndpoint}
         |          use-query-string:   ${deviceBootstrapParams.authentication.useQueryString.toString}
         |          scopes-api:         ${deviceBootstrapParams.authentication.scopes.api}
         |          scopes-core:        ${deviceBootstrapParams.authentication.scopes.core}
         |        server-api:
         |          url:                ${deviceBootstrapParams.serverApi.url}
         |          context-enabled:    ${deviceBootstrapParams.serverApi.context.enabled.toString}
         |          context-protocol:   ${deviceBootstrapParams.serverApi.context.protocol}
         |        server-core:
         |          address:            ${deviceBootstrapParams.serverCore.address}
         |          context-enabled:    ${deviceBootstrapParams.serverCore.context.enabled.toString}
         |          context-protocol:   ${deviceBootstrapParams.serverCore.context.protocol}
         |        secrets:
         |          derivation:
         |            encryption:
         |              secret-size:    ${deviceBootstrapParams.secrets.derivation.encryption.secretSize.toString}  bytes
         |              iterations:     ${deviceBootstrapParams.secrets.derivation.encryption.iterations.toString}
         |              salt-prefix:    ${deviceBootstrapParams.secrets.derivation.encryption.saltPrefix}
         |            authentication:
         |              enabled:        ${deviceBootstrapParams.secrets.derivation.authentication.enabled.toString}
         |              secret-size:    ${deviceBootstrapParams.secrets.derivation.authentication.secretSize.toString}  bytes
         |              iterations:     ${deviceBootstrapParams.secrets.derivation.authentication.iterations.toString}
         |              salt-prefix:    ${deviceBootstrapParams.secrets.derivation.authentication.saltPrefix}
         |          encryption:
         |            file:
         |              key-size:       ${deviceBootstrapParams.secrets.encryption.file.keySize.toString} bytes
         |              iv-size:        ${deviceBootstrapParams.secrets.encryption.file.ivSize.toString} bytes
         |            metadata:
         |              key-size:       ${deviceBootstrapParams.secrets.encryption.metadata.keySize.toString} bytes
         |              iv-size:        ${deviceBootstrapParams.secrets.encryption.metadata.ivSize.toString} bytes
         |            device-secret:
         |              key-size:       ${deviceBootstrapParams.secrets.encryption.deviceSecret.keySize.toString} bytes
         |              iv-size:        ${deviceBootstrapParams.secrets.encryption.deviceSecret.ivSize.toString} bytes
         |        additional-config:    ${deviceBootstrapParams.additionalConfig.fields.nonEmpty.toString}
         |)""".stripMargin
    )

    (apiServices, coreServices)
  } match {
    case Success((apiServices: ApiServices, coreServices: CoreServices)) =>
      val bootstrap = BootstrapProvider(
        bootstrapConfig = rawConfig.getConfig("service.bootstrap"),
        persistence = apiServices.persistence.combineWith(coreServices.persistence),
        entityProviders = Seq(
          new DatasetDefinitionBootstrapEntityProvider(apiServices.persistence.datasetDefinitions),
          new DeviceBootstrapEntityProvider(apiServices.persistence.devices),
          new NodeBootstrapEntityProvider(coreServices.persistence.nodes),
          new ScheduleBootstrapEntityProvider(apiServices.persistence.schedules),
          new UserBootstrapEntityProvider(apiServices.persistence.users)
        )
      )

      bootstrap
        .run()
        .flatMap {
          case mode @ (BootstrapProvider.BootstrapMode.Off | BootstrapProvider.BootstrapMode.InitAndStart) =>
            coreServices.persistence.startup().map(_ => mode)

          case mode =>
            Future.successful(mode)
        }
        .onComplete {
          case Success(mode @ (BootstrapProvider.BootstrapMode.Off | BootstrapProvider.BootstrapMode.InitAndStart)) =>
            if (mode == BootstrapProvider.BootstrapMode.InitAndStart) {
              log.info("Bootstrap complete with mode [{}]", mode.name)
              log.warn("Disable bootstrap mode and restart the service to avoid re-running the bootstrap process again...")
            }

            log.info("Service API starting on [{}:{}]...", apiConfig.interface, apiConfig.port)

            val _ = apiServices.apiEndpoint.start(
              interface = apiConfig.interface,
              port = apiConfig.port,
              context = apiServices.context
            )

            apiServices.bootstrapEndpoint.foreach { bootstrapEndpoint =>
              log.info("Bootstrap API starting on [{}:{}]...", bootstrapApiConfig.interface, bootstrapApiConfig.port)

              val _ = bootstrapEndpoint.start(
                interface = bootstrapApiConfig.interface,
                port = bootstrapApiConfig.port,
                context = bootstrapApiConfig.context
              )
            }

            log.info("Service core starting on [{}:{}]...", coreConfig.interface, coreConfig.port)

            val _ = coreServices.endpoint.start(
              interface = coreConfig.interface,
              port = coreConfig.port,
              context = coreServices.context
            )

            serviceState.set(State.Started(apiServices, coreServices))

          case Success(mode @ (BootstrapProvider.BootstrapMode.Init | BootstrapProvider.BootstrapMode.Drop)) =>
            log.info("Bootstrap complete with mode [{}]", mode.name)
            log.warn("Service will NOT be started; disable bootstrap mode and restart the service to continue...")
            serviceState.set(State.BootstrapComplete)

          case Failure(e) =>
            log.error("Bootstrap failed: [{} - {}]", e.getClass.getSimpleName, e.getMessage)
            serviceState.set(State.BootstrapFailed(e))
            stop()
        }

    case Failure(e) =>
      log.error("Service startup failed: [{} - {}]", e.getClass.getSimpleName, e.getMessage)
      serviceState.set(State.StartupFailed(e))
      stop()
  }

  locally {
    val _ = sys.addShutdownHook(stop())
  }

  def stop(): Unit = {
    log.info("Service stopping...")
    locally { val _ = exporter.shutdown() }
    locally { val _ = system.terminate() }
  }

  def state: State = serviceState.get()
}

object Service {
  final case class ApiServices(
    persistence: ServerPersistence,
    apiEndpoint: ApiEndpoint,
    bootstrapEndpoint: Option[BootstrapEndpoint],
    context: Option[EndpointContext]
  )

  final case class CoreServices(
    persistence: CorePersistence,
    endpoint: HttpEndpoint,
    context: Option[EndpointContext]
  )

  sealed trait State
  object State {
    case object Starting extends State

    final case class Started(
      apiServices: ApiServices,
      coreServices: CoreServices
    ) extends State

    final case object BootstrapComplete extends State

    final case class BootstrapFailed(throwable: Throwable) extends State

    final case class StartupFailed(throwable: Throwable) extends State
  }

  final case class Config(
    interface: String,
    port: Int,
    context: Option[EndpointContext]
  )

  object Config {
    def apply(config: typesafe.Config): Config =
      Config(
        interface = config.getString("interface"),
        port = config.getInt("port"),
        context = EndpointContext(config.getConfig("context"))
      )

    final case class UserAuthenticator(
      issuer: String,
      audience: String,
      identityClaim: String,
      jwksEndpoint: String,
      refreshInterval: FiniteDuration,
      refreshRetryInterval: FiniteDuration,
      expirationTolerance: FiniteDuration
    )

    object UserAuthenticator {
      def apply(config: typesafe.Config): UserAuthenticator =
        UserAuthenticator(
          issuer = config.getString("issuer"),
          audience = config.getString("audience"),
          identityClaim = config.getString("identity-claim"),
          jwksEndpoint = config.getString("jwks-endpoint"),
          refreshInterval = config.getDuration("refresh-interval").toMillis.millis,
          refreshRetryInterval = config.getDuration("refresh-retry-interval").toMillis.millis,
          expirationTolerance = config.getDuration("expiration-tolerance").toMillis.millis
        )
    }

    final case class IdentityCredentialsManager(
      url: String,
      managementUser: String,
      managementUserPassword: String,
      managementUserScope: String,
      contextConfig: typesafe.Config
    )

    object IdentityCredentialsManager {
      def apply(config: typesafe.Config): IdentityCredentialsManager =
        IdentityCredentialsManager(
          url = config.getString("url"),
          managementUser = config.getString("management.user"),
          managementUserPassword = config.getString("management.user-password"),
          managementUserScope = config.getString("management.scope"),
          contextConfig = config.getConfig("context")
        )
    }

    final case class NodeAuthenticator(
      issuer: String,
      audience: String,
      identityClaim: String,
      jwksEndpoint: String,
      refreshInterval: FiniteDuration,
      refreshRetryInterval: FiniteDuration,
      expirationTolerance: FiniteDuration
    )

    object NodeAuthenticator {
      def apply(config: typesafe.Config): NodeAuthenticator =
        NodeAuthenticator(
          issuer = config.getString("issuer"),
          audience = config.getString("audience"),
          identityClaim = config.getString("identity-claim"),
          jwksEndpoint = config.getString("jwks-endpoint"),
          refreshInterval = config.getDuration("refresh-interval").toMillis.millis,
          refreshRetryInterval = config.getDuration("refresh-retry-interval").toMillis.millis,
          expirationTolerance = config.getDuration("expiration-tolerance").toMillis.millis
        )
    }

    final case class InstanceAuthenticator(
      tokenEndpoint: String,
      clientId: String,
      clientSecret: String,
      expirationTolerance: FiniteDuration,
      useQueryString: Boolean
    )

    object InstanceAuthenticator {
      def apply(config: typesafe.Config): InstanceAuthenticator =
        InstanceAuthenticator(
          tokenEndpoint = config.getString("token-endpoint"),
          clientId = config.getString("client-id"),
          clientSecret = config.getString("client-secret"),
          expirationTolerance = config.getDuration("expiration-tolerance").toMillis.millis,
          useQueryString = config.getBoolean("use-query-string")
        )
    }

    final case class BootstrapApiConfig(
      enabled: Boolean,
      interface: String,
      port: Int,
      context: Option[EndpointContext]
    )

    object BootstrapApiConfig {
      def apply(config: typesafe.Config): BootstrapApiConfig =
        BootstrapApiConfig(
          enabled = config.getBoolean("enabled"),
          interface = config.getString("interface"),
          port = config.getInt("port"),
          context = EndpointContext(config.getConfig("context"))
        )
    }

    final case class DeviceBootstrap(
      codeSize: Int,
      codeExpiration: FiniteDuration,
      secretSize: Int,
      credentialsManager: DeviceBootstrap.IdentityCredentialsManager
    )

    object DeviceBootstrap {
      def config(config: typesafe.Config): DeviceBootstrap =
        DeviceBootstrap(
          codeSize = config.getInt("code-size"),
          codeExpiration = config.getDuration("code-expiration").toMillis.millis,
          secretSize = config.getInt("secret-size"),
          credentialsManager = DeviceBootstrap.IdentityCredentialsManager(
            clientRedirectUri = config.getString("credentials-manager.identity.client.redirect-uri"),
            clientTokenExpiration = config.getDuration("credentials-manager.identity.client.token-expiration").toMillis.millis
          )
        )

      final case class IdentityCredentialsManager(
        clientRedirectUri: String,
        clientTokenExpiration: FiniteDuration
      )

      def params(config: typesafe.Config): DeviceBootstrapParameters = {
        val additionalConfigFile = config.getString("additional-config")
        val additionalConfig = if (additionalConfigFile.nonEmpty) {
          typesafe.ConfigFactory.parseResourcesAnySyntax(additionalConfigFile)
        } else {
          typesafe.ConfigFactory.empty()
        }

        DeviceBootstrapParameters(
          authentication = DeviceBootstrapParameters.Authentication(
            tokenEndpoint = config.getString("authentication.token-endpoint"),
            clientId = "", // provided during bootstrap execution
            clientSecret = "", // provided during bootstrap execution
            useQueryString = config.getBoolean("authentication.use-query-string"),
            scopes = DeviceBootstrapParameters.Scopes(
              api = config.getString("authentication.scopes.api"),
              core = config.getString("authentication.scopes.core")
            ),
            context = EndpointContext.Encoded(
              config = config.getConfig("authentication.context")
            )
          ),
          serverApi = DeviceBootstrapParameters.ServerApi(
            url = config.getString("server-api.url"),
            user = "", // provided during bootstrap execution
            userSalt = "", // provided during bootstrap execution
            device = "", // provided during bootstrap execution
            context = EndpointContext.Encoded(
              config = config.getConfig("server-api.context")
            )
          ),
          serverCore = DeviceBootstrapParameters.ServerCore(
            address = config.getString("server-core.address"),
            nodeId = "", // provided during bootstrap execution
            context = EndpointContext.Encoded(
              config = config.getConfig("server-core.context")
            )
          ),
          secrets = SecretsConfig(
            config = config.getConfig("secrets"),
            ivSize = SecretsConfig.Encryption.MinIvSize // value always overridden by client
          ),
          additionalConfig = Json
            .parse(additionalConfig.root().render(typesafe.ConfigRenderOptions.concise()))
            .as[JsObject]
        )
      }
    }
  }

  object Telemetry {
    final val Instrumentation: String = "stasis_server"

    def createMetricsExporter(config: Config): MetricsExporter =
      MetricsExporter.Prometheus.asProxyRegistry(
        instrumentation = Instrumentation,
        interface = config.interface,
        port = config.port
      ) { registry => DefaultExports.register(registry) }
  }
}
