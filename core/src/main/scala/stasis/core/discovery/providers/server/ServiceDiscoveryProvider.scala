package stasis.core.discovery.providers.server

import java.io.File
import java.util.concurrent.ConcurrentHashMap

import scala.concurrent.Future
import scala.jdk.CollectionConverters._

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import stasis.core.discovery.ServiceApiEndpoint
import stasis.core.discovery.ServiceDiscoveryRequest
import stasis.core.discovery.ServiceDiscoveryResult
import stasis.core.networking.grpc.GrpcEndpointAddress
import stasis.core.networking.http.HttpEndpointAddress

trait ServiceDiscoveryProvider {
  def provide(request: ServiceDiscoveryRequest): Future[ServiceDiscoveryResult]
}

object ServiceDiscoveryProvider {
  @SuppressWarnings(Array("org.wartremover.warts.Throw"))
  def apply(config: com.typesafe.config.Config): ServiceDiscoveryProvider =
    config.getString("type").trim.toLowerCase match {
      case "disabled" =>
        new Disabled()

      case "static" =>
        val configFile = config.getString("static.config").trim
        require(configFile.nonEmpty, "Static discovery enabled but no config file was provided")

        val endpointsConfig = com.typesafe.config.ConfigFactory
          .parseFile(
            Option(getClass.getClassLoader.getResource(configFile))
              .map(resource => new File(resource.getFile))
              .getOrElse(new File(configFile))
          )
          .resolve()
          .getConfig("endpoints")

        new Static(endpoints = Static.Endpoints(config = endpointsConfig))

      case other =>
        throw new IllegalArgumentException(s"Unexpected provider type specified: [$other]")
    }

  class Disabled extends ServiceDiscoveryProvider {
    override def provide(request: ServiceDiscoveryRequest): Future[ServiceDiscoveryResult] =
      Future.successful(ServiceDiscoveryResult.KeepExisting)
  }

  class Static(endpoints: Static.Endpoints) extends ServiceDiscoveryProvider {
    private val clients: ConcurrentHashMap.KeySetView[String, _] = ConcurrentHashMap.newKeySet()

    private val log: Logger = LoggerFactory.getLogger(this.getClass.getName)

    log.debug(
      "Service discovery enabled with endpoints [{}]",
      (endpoints.api.map(_.id) ++ endpoints.core.map(_.id) ++ endpoints.discovery.map(_.id))
        .mkString("\n\t", "\n\t", "\n")
    )

    override def provide(request: ServiceDiscoveryRequest): Future[ServiceDiscoveryResult] =
      Future.successful(
        if (request.isInitialRequest || !clients.contains(request.id)) {
          val _ = clients.add(request.id)
          ServiceDiscoveryResult.SwitchTo(
            endpoints = endpoints.select(forRequestId = request.id),
            recreateExisting = false
          )
        } else {
          ServiceDiscoveryResult.KeepExisting
        }
      )
  }

  object Static {
    final case class Endpoints(
      api: Seq[ServiceApiEndpoint.Api],
      core: Seq[ServiceApiEndpoint.Core],
      discovery: Seq[ServiceApiEndpoint.Discovery]
    ) {
      require(api.nonEmpty, "At least one API endpoint must be configured")
      require(core.nonEmpty, "At least one core endpoint must be configured")

      def select(forRequestId: String): ServiceDiscoveryResult.Endpoints = {
        val hash = forRequestId.hashCode

        val selectedApi = api(Math.abs(hash % api.length))
        val selectedCore = core(Math.abs(hash % core.length))
        val selectedDiscovery = if (discovery.isEmpty) {
          ServiceApiEndpoint.Discovery(uri = selectedApi.uri)
        } else {
          discovery(Math.abs(hash % discovery.length))
        }

        ServiceDiscoveryResult.Endpoints(
          api = selectedApi,
          core = selectedCore,
          discovery = selectedDiscovery
        )
      }
    }

    object Endpoints {
      @SuppressWarnings(Array("org.wartremover.warts.Throw"))
      def apply(config: com.typesafe.config.Config): Endpoints =
        Endpoints(
          api = config.getConfigList("api").asScala.toSeq.map { endpointConfig =>
            ServiceApiEndpoint.Api(uri = endpointConfig.getString("uri"))
          },
          core = config.getConfigList("core").asScala.toSeq.map { endpointConfig =>
            endpointConfig.getString("type").trim.toLowerCase match {
              case "http" =>
                ServiceApiEndpoint.Core(
                  address = HttpEndpointAddress(
                    uri = endpointConfig.getString("http.uri")
                  )
                )

              case "grpc" =>
                ServiceApiEndpoint.Core(
                  address = GrpcEndpointAddress(
                    host = endpointConfig.getString("grpc.host"),
                    port = endpointConfig.getInt("grpc.port"),
                    tlsEnabled = endpointConfig.getBoolean("grpc.tls-enabled")
                  )
                )

              case other =>
                throw new IllegalArgumentException(s"Unexpected endpoint type specified: [$other]")

            }
          },
          discovery = config.getConfigList("discovery").asScala.toSeq.map { endpointConfig =>
            ServiceApiEndpoint.Discovery(uri = endpointConfig.getString("uri"))
          }
        )
    }
  }
}
