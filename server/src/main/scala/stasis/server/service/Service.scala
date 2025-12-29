package stasis.server.service

import java.time.Instant
import java.util.concurrent.atomic.AtomicReference

import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.Failure
import scala.util.Success
import scala.util.Try

import com.typesafe.{config => typesafe}
import io.github.sndnv.layers
import io.github.sndnv.layers.events.EventCollector
import io.github.sndnv.layers.security.tls.EndpointContext
import io.github.sndnv.layers.service.BootstrapProvider
import io.github.sndnv.layers.service.actions.ActionExecutor
import io.github.sndnv.layers.service.components.Component
import io.github.sndnv.layers.service.components.auto._
import io.github.sndnv.layers.service.config.ConfigVerifier
import io.github.sndnv.layers.telemetry.DefaultTelemetryContext
import io.github.sndnv.layers.telemetry.TelemetryContext
import io.github.sndnv.layers.telemetry.analytics.AnalyticsCollector
import io.github.sndnv.layers.telemetry.metrics.MetricsExporter
import io.prometheus.client.hotspot.DefaultExports
import org.apache.pekko.actor.typed.ActorSystem
import org.apache.pekko.actor.typed.scaladsl.Behaviors
import org.apache.pekko.util.Timeout
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import stasis.core
import stasis.core.discovery.providers.server.ServiceDiscoveryProvider
import stasis.core.networking.http.HttpEndpoint
import stasis.server.BuildInfo
import stasis.server.api.ApiEndpoint
import stasis.server.api.BootstrapEndpoint
import stasis.server.security._
import stasis.server.service.actions.ActionDefinitionProvider
import stasis.server.service.bootstrap.DatasetDefinitionBootstrapEntityProvider
import stasis.server.service.bootstrap.DeviceBootstrapEntityProvider
import stasis.server.service.bootstrap.NodeBootstrapEntityProvider
import stasis.server.service.bootstrap.ScheduleBootstrapEntityProvider
import stasis.server.service.bootstrap.UserBootstrapEntityProvider
import stasis.server.service.components.ActionsComponentLoader
import stasis.server.service.components.Authenticators
import stasis.server.service.components.AuthenticatorsComponentLoader
import stasis.server.service.components.BootstrapEndpointComponentLoader
import stasis.server.service.components.CredentialsManagersComponentLoader
import stasis.server.service.components.EventsComponentLoader
import stasis.server.service.components.RoutingComponentLoader

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

  private implicit val ec: ExecutionContext = system.executionContext
  private implicit val log: Logger = LoggerFactory.getLogger(this.getClass.getName)
  private val exporter: MetricsExporter = Telemetry.createMetricsExporter(config = metricsConfig)

  Try {
    implicit val timeout: Timeout = rawConfig.getDuration("service.internal-query-timeout").toMillis.millis

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

    implicit val serverPersistence: ServerPersistence = ServerPersistence(persistenceConfig = rawConfig.getConfig("persistence"))
    implicit val corePersistence: CorePersistence = CorePersistence(persistenceConfig = rawConfig.getConfig("persistence"))

    implicit val resourceProvider: ResourceProvider = DefaultResourceProvider(
      resources = serverPersistence.resources ++ corePersistence.resources,
      users = serverPersistence.users.view()
    )

    implicit val actionsDefinitionProvider: ActionDefinitionProvider =
      ActionsComponentLoader.createActionDefinitionProvider(
        config = rawConfig.getConfig("actions"),
        corePersistence = corePersistence,
        serverPersistence = serverPersistence
      )

    implicit val authenticators: Component[Authenticators] =
      AuthenticatorsComponentLoader.create(config = rawConfig.getConfig("authenticators"))

    implicit val events: Component[EventCollector] =
      EventsComponentLoader.create(config = rawConfig.getConfig("events"))

    implicit val actions: Component[ActionExecutor] =
      ActionsComponentLoader.create(config = rawConfig.getConfig("actions"))

    implicit val credentialsManagers: Component[CredentialsManagers] =
      CredentialsManagersComponentLoader.create(config = rawConfig.getConfig("credentials-managers"))

    implicit val bootstrap: Component[BootstrapEndpoint] =
      BootstrapEndpointComponentLoader.create(config = rawConfig.getConfig("bootstrap"))

    val routing = RoutingComponentLoader.create(config = rawConfig.getConfig("routing"))

    val serviceDiscoveryProvider = ServiceDiscoveryProvider(config = rawConfig.getConfig("service.discovery"))

    val apiServices = ApiServices(
      persistence = serverPersistence,
      apiEndpoint = ApiEndpoint(
        resourceProvider = resourceProvider,
        eventCollector = events.component,
        authenticator = authenticators.component.users,
        userCredentialsManager = credentialsManagers.component.users,
        serviceDiscoveryProvider = serviceDiscoveryProvider,
        secretsConfig = bootstrap.component.parameters.secrets
      ),
      context = apiConfig.context
    )

    val coreServices = CoreServices(
      persistence = corePersistence,
      endpoint = HttpEndpoint(
        router = routing.component,
        reservationStore = corePersistence.reservations.view,
        authenticator = authenticators.component.nodes
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
         |    internal-query-timeout:  ${timeout.duration.toMillis.toString} ms
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
         |  ${AuthenticatorsComponentLoader.name}: ${authenticators.renderConfig("  ")}
         |
         |  ${CredentialsManagersComponentLoader.name}: ${credentialsManagers.renderConfig("  ")}
         |
         |  ${RoutingComponentLoader.name}: ${routing.renderConfig("  ")}
         |
         |  ${BootstrapEndpointComponentLoader.name}: ${bootstrap.renderConfig("  ")}
         |
         |  ${EventsComponentLoader.name}:
         |    ${EventsComponentLoader.componentName}: ${events.renderConfig("    ")}
         |
         |  ${ActionsComponentLoader.name}:
         |    ${ActionsComponentLoader.componentName}: ${actions.renderConfig("    ")}
         |)""".stripMargin
    )

    ConfigVerifier.verify()

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

            apiServices.apiEndpoint.start(
              interface = apiConfig.interface,
              port = apiConfig.port,
              context = apiServices.context
            )

            coreServices.endpoint.start(
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
    final case class Started(apiServices: ApiServices, coreServices: CoreServices) extends State
    case object BootstrapComplete extends State
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
