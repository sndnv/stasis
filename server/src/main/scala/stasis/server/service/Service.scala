package stasis.server.service

import java.util.UUID
import java.util.concurrent.atomic.AtomicReference
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorSystem, Behavior, SpawnProtocol}
import akka.http.scaladsl.model.headers.HttpCredentials
import akka.util.Timeout
import com.typesafe.{config => typesafe}
import org.slf4j.{Logger, LoggerFactory}
import play.api.libs.json.{JsObject, Json}
import stasis.core.networking.grpc.{GrpcEndpointAddress, GrpcEndpointClient}
import stasis.core.networking.http.{HttpEndpoint, HttpEndpointAddress, HttpEndpointClient}
import stasis.core.routing.{DefaultRouter, NodeProxy, Router}
import stasis.core.security.jwt.{DefaultJwtAuthenticator, DefaultJwtProvider, JwtProvider}
import stasis.core.security.keys.RemoteKeyProvider
import stasis.core.security.oauth.{DefaultOAuthClient, OAuthClient}
import stasis.core.security.tls.EndpointContext
import stasis.core.security.{JwtNodeAuthenticator, JwtNodeCredentialsProvider, NodeAuthenticator}
import stasis.server.api.routes.DeviceBootstrap
import stasis.server.api.{ApiEndpoint, BootstrapEndpoint}
import stasis.server.security._
import stasis.server.security.authenticators._
import stasis.server.security.devices._
import stasis.server.security.users.{IdentityUserCredentialsManager, UserCredentialsManager}
import stasis.server.service.Service.Config.BootstrapApiConfig
import stasis.shared.model.devices.DeviceBootstrapParameters
import stasis.shared.secrets.SecretsConfig

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._
import scala.util.{Failure, Success, Try}

trait Service {
  import Service._

  private val serviceState: AtomicReference[State] = new AtomicReference[State](State.Starting)

  private implicit val system: ActorSystem[SpawnProtocol.Command] = ActorSystem(
    Behaviors.setup(_ => SpawnProtocol()): Behavior[SpawnProtocol.Command],
    name = "stasis-server-service"
  )

  private implicit val ec: ExecutionContext = system.executionContext
  private implicit val log: Logger = LoggerFactory.getLogger(this.getClass.getName)

  protected def systemConfig: typesafe.Config = system.settings.config

  private val rawConfig: typesafe.Config = systemConfig.getConfig("stasis.server")
  private val apiConfig: Config = Config(rawConfig.getConfig("service.api"))
  private val coreConfig: Config = Config(rawConfig.getConfig("service.core"))
  private val bootstrapApiConfig: BootstrapApiConfig = BootstrapApiConfig(rawConfig.getConfig("bootstrap.api"))

  Try {
    implicit val timeout: Timeout = rawConfig.getDuration("service.internal-query-timeout").toMillis.millis

    val instanceAuthenticatorConfig = Config.InstanceAuthenticator(rawConfig.getConfig("authenticators.instance"))
    val serverId = UUID.fromString(instanceAuthenticatorConfig.clientId)

    val authenticationEndpointContext: Option[EndpointContext] =
      EndpointContext(rawConfig.getConfig("clients.authentication.context"))

    val oauthClient = DefaultOAuthClient(
      tokenEndpoint = instanceAuthenticatorConfig.tokenEndpoint,
      client = serverId.toString,
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
      context = EndpointContext(identityCredentialsManagerConfig.contextConfig),
      requestBufferSize = identityCredentialsManagerConfig.requestBufferSize
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
        context = EndpointContext(identityCredentialsManagerConfig.contextConfig),
        requestBufferSize = identityCredentialsManagerConfig.requestBufferSize
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
      requestBufferSize = rawConfig.getInt("clients.core.request-buffer-size")
    )

    val coreGrpcEndpointClient = GrpcEndpointClient(
      credentials = JwtNodeCredentialsProvider[GrpcEndpointAddress](
        nodeStore = corePersistence.nodes.view,
        underlying = clientJwtProvider
      ),
      context = clientEndpointContext
    )

    val nodeProxy = NodeProxy(
      httpClient = coreHttpEndpointClient,
      grpcClient = coreGrpcEndpointClient
    )

    val router: Router = DefaultRouter(
      routerId = serverId,
      persistence = DefaultRouter.Persistence(
        manifests = corePersistence.manifests,
        nodes = corePersistence.nodes.view,
        reservations = corePersistence.reservations,
        staging = corePersistence.staging
      ),
      nodeProxy = nodeProxy
    )

    val apiServices = ApiServices(
      persistence = serverPersistence,
      apiEndpoint = ApiEndpoint(
        resourceProvider = resourceProvider,
        authenticator = userAuthenticator,
        userCredentialsManager = userCredentialsManager,
        secretsConfig = deviceBootstrapParams.secrets
      ),
      bootstrapEndpoint = bootstrapEndpoint,
      context = EndpointContext(apiConfig.context)
    )

    val coreServices = CoreServices(
      persistence = corePersistence,
      endpoint = HttpEndpoint(
        router = router,
        reservationStore = corePersistence.reservations.view,
        authenticator = nodeAuthenticator
      ),
      context = EndpointContext(coreConfig.context)
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
         |    bootstrap:
         |      enabled: ${rawConfig.getBoolean("service.bootstrap.enabled").toString}
         |      config:  ${rawConfig.getString("service.bootstrap.config")}
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
         |      request-buffer-size:  ${identityCredentialsManagerConfig.requestBufferSize.toString}
         |      management:
         |        user:               ${identityCredentialsManagerConfig.managementUser}
         |        password-provided:  ${identityCredentialsManagerConfig.managementUserPassword.nonEmpty.toString}
         |        scope:              ${identityCredentialsManagerConfig.managementUserScope}
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
         |      enabled:         ${corePersistence.stagingStoreDescriptor.isDefined.toString}
         |      destaging-delay: ${corePersistence.stagingStoreDestagingDelay.toMillis.toString} ms
         |      store:           ${corePersistence.stagingStoreDescriptor.map(_.toString).getOrElse("none")}
         |
         |  bootstrap:
         |    enabled:   ${bootstrapApiConfig.enabled.toString}
         |    interface: ${bootstrapApiConfig.interface}
         |    port:      ${bootstrapApiConfig.port.toString}
         |    context:
         |      protocol: ${bootstrapApiConfig.context.protocol}
         |      keystore: ${bootstrapApiConfig.context.keyStoreConfig.map(_.storePath).getOrElse("none")}
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
         |)
       """.stripMargin
    )

    (apiServices, coreServices)
  } match {
    case Success((apiServices: ApiServices, coreServices: CoreServices)) =>
      Bootstrap
        .run(rawConfig.getConfig("service.bootstrap"), apiServices.persistence, coreServices.persistence)
        .onComplete {
          case Success(_) =>
            log.info("Service API starting on [{}:{}]...", apiConfig.interface, apiConfig.port)

            val _ = apiServices.apiEndpoint.start(
              interface = apiConfig.interface,
              port = apiConfig.port,
              context = Some(apiServices.context)
            )

            apiServices.bootstrapEndpoint.foreach { bootstrapEndpoint =>
              log.info("Bootstrap API starting on [{}:{}]...", bootstrapApiConfig.interface, bootstrapApiConfig.port)

              val _ = bootstrapEndpoint.start(
                interface = bootstrapApiConfig.interface,
                port = bootstrapApiConfig.port,
                context = Some(EndpointContext(bootstrapApiConfig.context))
              )
            }

            log.info("Service core starting on [{}:{}]...", coreConfig.interface, coreConfig.port)

            val _ = coreServices.endpoint.start(
              interface = coreConfig.interface,
              port = coreConfig.port,
              context = Some(coreServices.context)
            )

            serviceState.set(State.Started(apiServices, coreServices))

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
    val _ = system.terminate()
  }

  def state: State = serviceState.get()
}

object Service {
  final case class ApiServices(
    persistence: ServerPersistence,
    apiEndpoint: ApiEndpoint,
    bootstrapEndpoint: Option[BootstrapEndpoint],
    context: EndpointContext
  )

  final case class CoreServices(
    persistence: CorePersistence,
    endpoint: HttpEndpoint,
    context: EndpointContext
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
    context: EndpointContext.Config
  )

  object Config {
    def apply(config: typesafe.Config): Config =
      Config(
        interface = config.getString("interface"),
        port = config.getInt("port"),
        context = EndpointContext.Config(config.getConfig("context"))
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
      contextConfig: typesafe.Config,
      requestBufferSize: Int
    )

    object IdentityCredentialsManager {
      def apply(config: typesafe.Config): IdentityCredentialsManager =
        IdentityCredentialsManager(
          url = config.getString("url"),
          managementUser = config.getString("management.user"),
          managementUserPassword = config.getString("management.user-password"),
          managementUserScope = config.getString("management.scope"),
          contextConfig = config.getConfig("context"),
          requestBufferSize = config.getInt("request-buffer-size")
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
      context: EndpointContext.Config
    )

    object BootstrapApiConfig {
      def apply(config: typesafe.Config): BootstrapApiConfig =
        BootstrapApiConfig(
          enabled = config.getBoolean("enabled"),
          interface = config.getString("interface"),
          port = config.getInt("port"),
          context = EndpointContext.Config(config.getConfig("context"))
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
            context = DeviceBootstrapParameters.Context(
              config = config.getConfig("authentication.context")
            )
          ),
          serverApi = DeviceBootstrapParameters.ServerApi(
            url = config.getString("server-api.url"),
            user = "", // provided during bootstrap execution
            userSalt = "", // provided during bootstrap execution
            device = "", // provided during bootstrap execution
            context = DeviceBootstrapParameters.Context(
              config = config.getConfig("server-api.context")
            )
          ),
          serverCore = DeviceBootstrapParameters.ServerCore(
            address = config.getString("server-core.address"),
            nodeId = "", // provided during bootstrap execution
            context = DeviceBootstrapParameters.Context(
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
}
