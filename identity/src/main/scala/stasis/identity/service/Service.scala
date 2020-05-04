package stasis.identity.service

import java.util.concurrent.atomic.AtomicReference

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.scaladsl.adapter._
import akka.actor.typed.{ActorSystem, Behavior, SpawnProtocol}
import akka.event.{Logging, LoggingAdapter}
import akka.http.scaladsl.ConnectionContext
import com.typesafe.{config => typesafe}
import org.jose4j.jwk.JsonWebKey
import stasis.core.security.jwt.DefaultJwtAuthenticator
import stasis.core.security.keys.LocalKeyProvider
import stasis.core.security.tls.EndpointContext
import stasis.identity.api.{IdentityEndpoint, manage => manageApi, oauth => oauthApi}
import stasis.identity.authentication.{manage, oauth}
import stasis.identity.model.apis.Api
import stasis.identity.model.codes.generators.DefaultAuthorizationCodeGenerator
import stasis.identity.model.secrets.Secret
import stasis.identity.model.tokens.generators.{JwtBearerAccessTokenGenerator, RandomRefreshTokenGenerator}

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._
import scala.util.{Failure, Success, Try}

trait Service {
  import Service._

  private val serviceState: AtomicReference[State] = new AtomicReference[State](State.Starting)

  private implicit val system: ActorSystem[SpawnProtocol] = ActorSystem(
    Behaviors.setup(_ => SpawnProtocol.behavior): Behavior[SpawnProtocol],
    name = "stasis-identity-service"
  )

  private implicit val untyped: akka.actor.ActorSystem = system.toUntyped
  private implicit val ec: ExecutionContext = system.executionContext
  private implicit val log: LoggingAdapter = Logging(untyped, this.getClass.getName)

  protected def systemConfig: typesafe.Config = system.settings.config

  private val rawConfig: typesafe.Config = systemConfig.getConfig("stasis.identity")
  private val config: Config = Config(rawConfig.getConfig("service"))

  private implicit val clientSecretsConfig: Secret.ClientConfig =
    Secret.ClientConfig(rawConfig.getConfig("secrets.client"))

  private implicit val ownerSecretsConfig: Secret.ResourceOwnerConfig =
    Secret.ResourceOwnerConfig(rawConfig.getConfig("secrets.resource-owner"))

  Try {
    val refreshTokensConfig = Config.RefreshTokens(rawConfig.getConfig("tokens.refresh"))
    val accessTokensConfig = Config.AccessTokens(rawConfig.getConfig("tokens.access"))
    val authorizationCodesConfig = Config.AuthorizationCodes(rawConfig.getConfig("codes.authorization"))
    val ownerAuthenticatorConfig = Config.ResourceOwnerAuthenticator(rawConfig.getConfig("authenticators.resource-owner"))

    val persistence: Persistence = new Persistence(
      persistenceConfig = rawConfig.getConfig("persistence"),
      authorizationCodeExpiration = authorizationCodesConfig.expiration,
      refreshTokenExpiration = refreshTokensConfig.expiration
    )(system, config.internalQueryTimeout)

    val accessTokenSignatureKey: JsonWebKey =
      SignatureKey.fromConfig(rawConfig.getConfig("tokens.access.signature-key"))

    val oauthProviders = oauthApi.setup.Providers(
      apiStore = persistence.apis.view,
      clientStore = persistence.clients.view,
      refreshTokenStore = persistence.refreshTokens,
      authorizationCodeStore = persistence.authorizationCodes,
      accessTokenGenerator = new JwtBearerAccessTokenGenerator(
        issuer = accessTokensConfig.issuer,
        jwk = accessTokenSignatureKey,
        jwtExpiration = accessTokensConfig.expiration
      ),
      authorizationCodeGenerator = new DefaultAuthorizationCodeGenerator(
        codeSize = authorizationCodesConfig.size
      ),
      refreshTokenGenerator = new RandomRefreshTokenGenerator(
        tokenSize = refreshTokensConfig.size
      ),
      clientAuthenticator = new oauth.DefaultClientAuthenticator(
        store = persistence.clients.view,
        secretConfig = clientSecretsConfig
      ),
      resourceOwnerAuthenticator = new oauth.DefaultResourceOwnerAuthenticator(
        store = persistence.resourceOwners.view,
        secretConfig = ownerSecretsConfig
      )
    )

    val ownerAuthenticator = new manage.DefaultResourceOwnerAuthenticator(
      store = persistence.resourceOwners.view,
      underlying = new DefaultJwtAuthenticator(
        provider = new LocalKeyProvider(
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

    val endpoint = new IdentityEndpoint(
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

    val context = EndpointContext.create(config.context)

    log.info(
      s"""
         |Config(
         |  realm: $realm
         |
         |  bootstrap:
         |    enabled: ${rawConfig.getBoolean("bootstrap.enabled")}
         |    config:  ${rawConfig.getString("bootstrap.config")}
         |
         |  service:
         |    interface: ${config.interface}
         |    port:      ${config.port}
         |    iqt:       ${config.internalQueryTimeout.toMillis} ms
         |    context:
         |      protocol: ${config.context.protocol}
         |      keystore: ${config.context.keyStoreConfig.map(_.storePath).getOrElse("none")}
         |
         |  authorization-codes:
         |    size:       ${authorizationCodesConfig.size}
         |    expiration: ${authorizationCodesConfig.expiration.toSeconds} s
         |
         |  access-tokens:
         |    issuer:        ${accessTokensConfig.issuer}
         |    expiration:    ${accessTokensConfig.expiration.toSeconds} s
         |    signature-key: ${accessTokenSignatureKey.getKeyId}
         |
         |  refresh-tokens:
         |    allowed:    ${refreshTokensConfig.allowed}
         |    size:       ${refreshTokensConfig.size}
         |    expiration: ${refreshTokensConfig.expiration.toSeconds} s
         |
         |  client-secrets:
         |    algorithm:            ${clientSecretsConfig.algorithm}
         |    iterations:           ${clientSecretsConfig.iterations}
         |    key-size:             ${clientSecretsConfig.derivedKeySize} bytes
         |    salt-size:            ${clientSecretsConfig.saltSize} bytes
         |    authentication-delay: ${clientSecretsConfig.authenticationDelay.toMillis} ms
         |
         |  resource-owner-secrets:
         |    algorithm:            ${ownerSecretsConfig.algorithm}
         |    iterations:           ${ownerSecretsConfig.iterations}
         |    key-size:             ${ownerSecretsConfig.derivedKeySize} bytes
         |    salt-size:            ${ownerSecretsConfig.saltSize} bytes
         |    authentication-delay: ${ownerSecretsConfig.authenticationDelay.toMillis} ms
         |
         |  resource-owner-authenticator:
         |    identity-claim:       ${ownerAuthenticatorConfig.identityClaim}
         |    expiration-tolerance: ${ownerAuthenticatorConfig.expirationTolerance.toMillis} ms
         |
         |  database:
         |    profile:    ${persistence.profile.getClass.getSimpleName}
         |    url:        ${persistence.databaseUrl}
         |    driver:     ${persistence.databaseDriver}
         |    keep-alive: ${persistence.databaseKeepAlive}
         |)
       """.stripMargin
    )

    (persistence, endpoint, context)
  } match {
    case Success((persistence: Persistence, endpoint: IdentityEndpoint, context: ConnectionContext)) =>
      Bootstrap
        .run(rawConfig.getConfig("bootstrap"), persistence)
        .onComplete {
          case Success(_) =>
            log.info("Identity service starting on [{}:{}]...", config.interface, config.port)
            serviceState.set(State.Started(persistence, endpoint))
            val _ = endpoint.start(
              interface = config.interface,
              port = config.port,
              context = context
            )

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
    log.info("Identity service stopping...")
    val _ = system.terminate()
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
    internalQueryTimeout: FiniteDuration,
    context: EndpointContext.ContextConfig
  )

  object Config {
    def apply(config: com.typesafe.config.Config): Config =
      Config(
        interface = config.getString("interface"),
        port = config.getInt("port"),
        internalQueryTimeout = config.getDuration("internal-query-timeout").toMillis.millis,
        context = EndpointContext.ContextConfig(config.getConfig("context"))
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
}
