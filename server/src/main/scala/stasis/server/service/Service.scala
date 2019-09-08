package stasis.server.service

import java.util.concurrent.atomic.AtomicReference

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.scaladsl.adapter._
import akka.actor.typed.{ActorSystem, Behavior, SpawnProtocol}
import akka.event.{Logging, LoggingAdapter}
import akka.http.scaladsl.ConnectionContext
import akka.util.Timeout
import com.typesafe.{config => typesafe}
import stasis.core.security.jwt.{JwtAuthenticator, RemoteKeyProvider}
import stasis.core.security.tls.EndpointContext
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
  private val config: Config = Config(rawConfig.getConfig("service"))

  Try {
    implicit val timeout: Timeout = config.internalQueryTimeout

    val persistenceConfig = rawConfig.getConfig("persistence")

    val apiPersistence: ApiPersistence = new ApiPersistence(persistenceConfig)

    val resources: Set[Resource] = Set(
      apiPersistence.datasetDefinitions.manage(),
      apiPersistence.datasetDefinitions.manageSelf(),
      apiPersistence.datasetDefinitions.view(),
      apiPersistence.datasetDefinitions.viewSelf(),
      apiPersistence.datasetEntries.manage(),
      apiPersistence.datasetEntries.manageSelf(),
      apiPersistence.datasetEntries.view(),
      apiPersistence.datasetEntries.viewSelf(),
      apiPersistence.devices.manage(),
      apiPersistence.devices.manageSelf(),
      apiPersistence.devices.view(),
      apiPersistence.devices.viewSelf(),
      apiPersistence.schedules.manage(),
      apiPersistence.schedules.view(),
      apiPersistence.users.manage(),
      apiPersistence.users.manageSelf(),
      apiPersistence.users.view(),
      apiPersistence.users.viewSelf()
    )

    val resourceProvider: ResourceProvider = new DefaultResourceProvider(
      resources = resources,
      users = apiPersistence.users.view()
    )

    val userAuthenticatorConfig = rawConfig.getConfig("authenticators.user")
    val userAuthenticator: UserAuthenticator = new DefaultUserAuthenticator(
      store = apiPersistence.users.view(),
      underlying = new JwtAuthenticator(
        provider = RemoteKeyProvider(
          jwksEndpoint = userAuthenticatorConfig.getString("jwks-endpoint"),
          refreshInterval = userAuthenticatorConfig.getDuration("refresh-interval").getSeconds.seconds,
          issuer = userAuthenticatorConfig.getString("issuer")
        ),
        audience = userAuthenticatorConfig.getString("audience"),
        expirationTolerance = userAuthenticatorConfig.getDuration("expiration-tolerance").toMillis.millis
      )
    )

    val apiEndpoint = new ApiEndpoint(
      resourceProvider = resourceProvider,
      authenticator = userAuthenticator
    )

    val context = EndpointContext.create(config.context)

    (apiPersistence, apiEndpoint, context)
  } match {
    case Success((persistence: ApiPersistence, apiEndpoint: ApiEndpoint, context: ConnectionContext)) =>
      Bootstrap
        .run(rawConfig.getConfig("bootstrap"), persistence)
        .onComplete {
          case Success(_) =>
            log.info("Service API starting on [{}:{}]...", config.interface, config.port)
            serviceState.set(State.Started(persistence, apiEndpoint))
            val _ = apiEndpoint.start(
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
    log.info("Service stopping...")
    val _ = system.terminate()
  }

  def state: State = serviceState.get()
}

object Service {
  sealed trait State
  object State {
    case object Starting extends State
    final case class Started(persistence: ApiPersistence, apiEndpoint: ApiEndpoint) extends State
    final case class BootstrapFailed(throwable: Throwable) extends State
    final case class StartupFailed(throwable: Throwable) extends State
  }

  final case class Config(
    interface: String,
    port: Int,
    internalQueryTimeout: FiniteDuration,
    context: EndpointContext.Config
  )

  object Config {
    def apply(config: com.typesafe.config.Config): Config =
      Config(
        interface = config.getString("interface"),
        port = config.getInt("port"),
        internalQueryTimeout = config.getDuration("internal-query-timeout").toMillis.millis,
        context = EndpointContext.Config(config.getConfig("context"))
      )
  }
}
