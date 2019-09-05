package stasis.server.service

import java.util.concurrent.atomic.AtomicReference

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.scaladsl.adapter._
import akka.actor.typed.{ActorSystem, Behavior, SpawnProtocol}
import akka.event.{Logging, LoggingAdapter}
import akka.http.scaladsl.ConnectionContext
import com.typesafe.{config => typesafe}
import stasis.core.security.jwt.{JwtAuthenticator, RemoteKeyProvider}
import stasis.core.security.tls.EndpointContext
import stasis.server.api.ServerEndpoint
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
    val persistence: Persistence = new Persistence(
      persistenceConfig = rawConfig.getConfig("persistence")
    )

    val resources: Set[Resource] = Set(
      persistence.datasetDefinitions.manage(),
      persistence.datasetDefinitions.manageSelf(),
      persistence.datasetDefinitions.view(),
      persistence.datasetDefinitions.viewSelf(),
      persistence.datasetEntries.manage(),
      persistence.datasetEntries.manageSelf(),
      persistence.datasetEntries.view(),
      persistence.datasetEntries.viewSelf(),
      persistence.devices.manage(),
      persistence.devices.manageSelf(),
      persistence.devices.view(),
      persistence.devices.viewSelf(),
      persistence.schedules.manage(),
      persistence.schedules.view(),
      persistence.users.manage(),
      persistence.users.manageSelf(),
      persistence.users.view(),
      persistence.users.viewSelf()
    )

    val resourceProvider: ResourceProvider = new DefaultResourceProvider(
      resources = resources,
      users = persistence.users.view()
    )

    val authenticatorConfig = rawConfig.getConfig("authenticators.user")
    val authenticator: UserAuthenticator = new DefaultUserAuthenticator(
      store = persistence.users.view(),
      underlying = new JwtAuthenticator(
        provider = RemoteKeyProvider(
          jwksEndpoint = authenticatorConfig.getString("jwks-endpoint"),
          refreshInterval = authenticatorConfig.getDuration("refresh-interval").getSeconds.seconds,
          issuer = authenticatorConfig.getString("issuer")
        )(system, config.internalQueryTimeout),
        audience = authenticatorConfig.getString("audience"),
        expirationTolerance = authenticatorConfig.getDuration("expiration-tolerance").toMillis.millis
      )
    )

    val endpoint = new ServerEndpoint(
      resourceProvider = resourceProvider,
      authenticator = authenticator
    )

    val context = EndpointContext.create(config.context)

    (persistence, endpoint, context)
  } match {
    case Success((persistence: Persistence, endpoint: ServerEndpoint, context: ConnectionContext)) =>
      Bootstrap
        .run(rawConfig.getConfig("bootstrap"), persistence)
        .onComplete {
          case Success(_) =>
            log.info("Service starting on [{}:{}]...", config.interface, config.port)
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
    log.info("Service stopping...")
    val _ = system.terminate()
  }

  def state: State = serviceState.get()
}

object Service {
  sealed trait State
  object State {
    case object Starting extends State
    final case class Started(persistence: Persistence, endpoint: ServerEndpoint) extends State
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
