package stasis.identity.service

import java.time.Instant
import java.util.concurrent.atomic.AtomicReference

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._
import scala.util.Failure
import scala.util.Success
import scala.util.Try

import com.typesafe.{config => typesafe}
import io.prometheus.client.hotspot.DefaultExports
import org.apache.pekko.actor.typed.ActorSystem
import org.apache.pekko.actor.typed.scaladsl.Behaviors
import org.apache.pekko.util.Timeout
import org.jose4j.jwk.JsonWebKey
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import stasis.identity.BuildInfo
import stasis.identity.api.IdentityEndpoint
import stasis.identity.api.{manage => manageApi}
import stasis.identity.api.{oauth => oauthApi}
import stasis.identity.authentication.manage
import stasis.identity.authentication.oauth
import stasis.identity.model.apis.Api
import stasis.identity.model.codes.generators.DefaultAuthorizationCodeGenerator
import stasis.identity.model.secrets.Secret
import stasis.identity.model.tokens.generators.JwtBearerAccessTokenGenerator
import stasis.identity.model.tokens.generators.RandomRefreshTokenGenerator
import stasis.layers
import stasis.layers.security.jwt.DefaultJwtAuthenticator
import stasis.layers.security.keys.LocalKeyProvider
import stasis.layers.security.tls.EndpointContext
import stasis.layers.telemetry.DefaultTelemetryContext
import stasis.layers.telemetry.TelemetryContext
import stasis.layers.telemetry.metrics.MetricsExporter

trait Service {
  import Service._

  private val serviceState: AtomicReference[State] = new AtomicReference[State](State.Starting)

  private implicit val system: ActorSystem[Nothing] = ActorSystem(
    guardianBehavior = Behaviors.ignore,
    name = "stasis-identity-service"
  )

  protected def systemConfig: typesafe.Config = system.settings.config

  private val rawConfig: typesafe.Config = systemConfig.getConfig("stasis.identity")
  private val apiConfig: Config = Config(rawConfig.getConfig("service.api"))
  private val metricsConfig: Config = Config(rawConfig.getConfig("service.telemetry.metrics"))

  private implicit val clientSecretsConfig: Secret.ClientConfig =
    Secret.ClientConfig(rawConfig.getConfig("secrets.client"))

  private implicit val ownerSecretsConfig: Secret.ResourceOwnerConfig =
    Secret.ResourceOwnerConfig(rawConfig.getConfig("secrets.resource-owner"))

  private implicit val ec: ExecutionContext = system.executionContext
  private implicit val log: Logger = LoggerFactory.getLogger(this.getClass.getName)
  private val exporter: MetricsExporter = Telemetry.createMetricsExporter(config = metricsConfig)

  Try {
    implicit val timeout: Timeout = rawConfig.getDuration("service.internal-query-timeout").toMillis.millis

    val refreshTokensConfig = Config.RefreshTokens(rawConfig.getConfig("tokens.refresh"))
    val accessTokensConfig = Config.AccessTokens(rawConfig.getConfig("tokens.access"))
    val authorizationCodesConfig = Config.AuthorizationCodes(rawConfig.getConfig("codes.authorization"))
    val ownerAuthenticatorConfig = Config.ResourceOwnerAuthenticator(rawConfig.getConfig("authenticators.resource-owner"))

    implicit val telemetry: TelemetryContext = DefaultTelemetryContext(
      metricsProviders = Set(
        layers.security.Metrics.default(meter = exporter.meter, namespace = Telemetry.Instrumentation),
        layers.api.Metrics.default(meter = exporter.meter, namespace = Telemetry.Instrumentation),
        layers.persistence.Metrics.default(meter = exporter.meter, namespace = Telemetry.Instrumentation)
      ).flatten
    )

    val persistence: Persistence = Persistence(
      persistenceConfig = rawConfig.getConfig("persistence"),
      authorizationCodeExpiration = authorizationCodesConfig.expiration,
      refreshTokenExpiration = refreshTokensConfig.expiration
    )

    val accessTokenSignatureKey: JsonWebKey =
      SignatureKey.fromConfig(rawConfig.getConfig("tokens.access.signature-key"))

    val oauthProviders = oauthApi.setup.Providers(
      apiStore = persistence.apis.view,
      clientStore = persistence.clients.view,
      resourceOwnerStore = persistence.resourceOwners.view,
      refreshTokenStore = persistence.refreshTokens,
      authorizationCodeStore = persistence.authorizationCodes,
      accessTokenGenerator = JwtBearerAccessTokenGenerator(
        issuer = accessTokensConfig.issuer,
        jwk = accessTokenSignatureKey,
        jwtExpiration = accessTokensConfig.expiration
      ),
      authorizationCodeGenerator = DefaultAuthorizationCodeGenerator(
        codeSize = authorizationCodesConfig.size
      ),
      refreshTokenGenerator = RandomRefreshTokenGenerator(
        tokenSize = refreshTokensConfig.size
      ),
      clientAuthenticator = oauth.DefaultClientAuthenticator(
        store = persistence.clients.view,
        secretConfig = clientSecretsConfig
      ),
      resourceOwnerAuthenticator = oauth.DefaultResourceOwnerAuthenticator(
        store = persistence.resourceOwners.view,
        secretConfig = ownerSecretsConfig
      )
    )

    val ownerAuthenticator = manage.DefaultResourceOwnerAuthenticator(
      store = persistence.resourceOwners.view,
      underlying = DefaultJwtAuthenticator(
        provider = LocalKeyProvider(
          jwk = accessTokenSignatureKey,
          issuer = accessTokensConfig.issuer
        ),
        audience = Api.ManageIdentity,
        identityClaim = ownerAuthenticatorConfig.identityClaim,
        expirationTolerance = ownerAuthenticatorConfig.expirationTolerance
      )
    )

    val manageProviders = manageApi.setup.Providers(
      apiStore = persistence.apis,
      clientStore = persistence.clients,
      codeStore = persistence.authorizationCodes,
      ownerStore = persistence.resourceOwners,
      tokenStore = persistence.refreshTokens,
      ownerAuthenticator = ownerAuthenticator
    )

    val realm: String = rawConfig.getString("realm")

    val endpoint = IdentityEndpoint(
      keys = Seq(accessTokenSignatureKey),
      oauthConfig = oauthApi.setup.Config(
        realm = realm,
        refreshTokensAllowed = refreshTokensConfig.allowed
      ),
      oauthProviders = oauthProviders,
      manageConfig = manageApi.setup.Config(
        realm = realm,
        clientSecrets = clientSecretsConfig,
        ownerSecrets = ownerSecretsConfig
      ),
      manageProviders = manageProviders
    )

    log.info(
      s"""
         |Build(
         |  name:    ${BuildInfo.name}
         |  version: ${BuildInfo.version}
         |  time:    ${Instant.ofEpochMilli(BuildInfo.time).toString}
         |)""".stripMargin
    )

    log.info(
      s"""
         |Config(
         |  realm: $realm
         |
         |  bootstrap:
         |    enabled: ${rawConfig.getBoolean("bootstrap.enabled").toString}
         |    config:  ${rawConfig.getString("bootstrap.config")}
         |
         |  service:
         |    internal-query-timeout: ${timeout.duration.toMillis.toString} ms
         |    api:
         |      interface: ${apiConfig.interface}
         |      port:      ${apiConfig.port.toString}
         |      context:
         |        enabled:  ${apiConfig.context.nonEmpty.toString}
         |        protocol: ${apiConfig.context.map(_.config.protocol).getOrElse("none")}
         |        keystore: ${apiConfig.context.flatMap(_.config.keyStoreConfig).map(_.storePath).getOrElse("none")}
         |
         |    telemetry:
         |      metrics:
         |        namespace: ${Telemetry.Instrumentation}
         |        interface: ${metricsConfig.interface}
         |        port:      ${metricsConfig.port.toString}
         |
         |  authorization-codes:
         |    size:       ${authorizationCodesConfig.size.toString}
         |    expiration: ${authorizationCodesConfig.expiration.toSeconds.toString} s
         |
         |  access-tokens:
         |    issuer:        ${accessTokensConfig.issuer}
         |    expiration:    ${accessTokensConfig.expiration.toSeconds.toString} s
         |    signature-key: ${accessTokenSignatureKey.getKeyId}
         |
         |  refresh-tokens:
         |    allowed:    ${refreshTokensConfig.allowed.toString}
         |    size:       ${refreshTokensConfig.size.toString}
         |    expiration: ${refreshTokensConfig.expiration.toSeconds.toString} s
         |
         |  client-secrets:
         |    algorithm:            ${clientSecretsConfig.algorithm}
         |    iterations:           ${clientSecretsConfig.iterations.toString}
         |    key-size:             ${clientSecretsConfig.derivedKeySize.toString} bytes
         |    salt-size:            ${clientSecretsConfig.saltSize.toString} bytes
         |    authentication-delay: ${clientSecretsConfig.authenticationDelay.toMillis.toString} ms
         |
         |  resource-owner-secrets:
         |    algorithm:            ${ownerSecretsConfig.algorithm}
         |    iterations:           ${ownerSecretsConfig.iterations.toString}
         |    key-size:             ${ownerSecretsConfig.derivedKeySize.toString} bytes
         |    salt-size:            ${ownerSecretsConfig.saltSize.toString} bytes
         |    authentication-delay: ${ownerSecretsConfig.authenticationDelay.toMillis.toString} ms
         |
         |  resource-owner-authenticator:
         |    identity-claim:       ${ownerAuthenticatorConfig.identityClaim}
         |    expiration-tolerance: ${ownerAuthenticatorConfig.expirationTolerance.toMillis.toString} ms
         |
         |  database:
         |    profile:    ${persistence.profile.getClass.getSimpleName}
         |    url:        ${persistence.databaseUrl}
         |    driver:     ${persistence.databaseDriver}
         |    keep-alive: ${persistence.databaseKeepAlive.toString}
         |)""".stripMargin
    )

    (persistence, endpoint, apiConfig.context)
  } match {
    case Success((persistence: Persistence, endpoint: IdentityEndpoint, context: Option[EndpointContext])) =>
      Bootstrap
        .run(rawConfig.getConfig("bootstrap"), persistence)
        .onComplete {
          case Success(_) =>
            log.info("Identity service starting on [{}:{}]...", apiConfig.interface, apiConfig.port)
            serviceState.set(State.Started(persistence, endpoint))
            val _ = endpoint.start(
              interface = apiConfig.interface,
              port = apiConfig.port,
              context = context
            )

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
    log.info("Identity service stopping...")
    locally { val _ = exporter.shutdown() }
    locally { val _ = system.terminate() }
  }

  def state: State = serviceState.get()
}

object Service {
  sealed trait State
  object State {
    case object Starting extends State
    final case class Started(persistence: Persistence, endpoint: IdentityEndpoint) extends State
    final case class BootstrapFailed(throwable: Throwable) extends State
    final case class StartupFailed(throwable: Throwable) extends State
  }

  final case class Config(
    interface: String,
    port: Int,
    context: Option[EndpointContext]
  )

  object Config {
    def apply(config: com.typesafe.config.Config): Config =
      Config(
        interface = config.getString("interface"),
        port = config.getInt("port"),
        context = EndpointContext(config.getConfig("context"))
      )

    final case class AuthorizationCodes(
      size: Int,
      expiration: FiniteDuration
    )

    object AuthorizationCodes {
      def apply(config: com.typesafe.config.Config): AuthorizationCodes =
        AuthorizationCodes(
          size = config.getInt("size"),
          expiration = config.getDuration("expiration").toMillis.millis
        )
    }

    final case class AccessTokens(
      issuer: String,
      expiration: FiniteDuration
    )

    object AccessTokens {
      def apply(config: com.typesafe.config.Config): AccessTokens =
        AccessTokens(
          issuer = config.getString("issuer"),
          expiration = config.getDuration("expiration").toMillis.millis
        )
    }

    final case class RefreshTokens(
      allowed: Boolean,
      size: Int,
      expiration: FiniteDuration
    )

    object RefreshTokens {
      def apply(config: com.typesafe.config.Config): RefreshTokens =
        RefreshTokens(
          allowed = config.getBoolean("allowed"),
          size = config.getInt("size"),
          expiration = config.getDuration("expiration").toMillis.millis
        )
    }

    final case class ResourceOwnerAuthenticator(
      identityClaim: String,
      expirationTolerance: FiniteDuration
    )

    object ResourceOwnerAuthenticator {
      def apply(config: com.typesafe.config.Config): ResourceOwnerAuthenticator =
        ResourceOwnerAuthenticator(
          identityClaim = config.getString("identity-claim"),
          expirationTolerance = config.getDuration("expiration-tolerance").toMillis.millis
        )
    }
  }

  object Telemetry {
    final val Instrumentation: String = "stasis_identity"

    def createMetricsExporter(config: Config): MetricsExporter =
      MetricsExporter.Prometheus.asProxyRegistry(
        instrumentation = Instrumentation,
        interface = config.interface,
        port = config.port
      ) { registry => DefaultExports.register(registry) }
  }
}
