package stasis.client.service.components

import java.util.UUID

import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.Try

import stasis.client.api.clients._
import stasis.core.api.PoolClient
import stasis.core.discovery.ServiceApiClient
import stasis.core.discovery.ServiceApiEndpoint
import stasis.core.discovery.ServiceDiscoveryClient
import stasis.core.discovery.http.HttpServiceDiscoveryClient
import stasis.core.discovery.providers.client.ServiceDiscoveryProvider
import stasis.core.networking.http.HttpEndpointAddress
import stasis.core.routing.Node
import io.github.sndnv.layers.security.tls.EndpointContext
import io.github.sndnv.layers.telemetry.analytics.AnalyticsClient
import stasis.shared.model.devices.Device
import stasis.shared.model.users.User

trait ApiClients {
  def clients: Clients
}

object ApiClients {
  def apply(base: Base, secrets: Secrets): Future[ApiClients] = {
    import base._
    import secrets._

    Future
      .fromTry(
        Try {
          val user = UUID.fromString(rawConfig.getString("server.api.user"))
          val device = UUID.fromString(rawConfig.getString("server.api.device"))
          val node = UUID.fromString(rawConfig.getString("server.core.node-id"))

          def createServerCoreEndpointClient(address: HttpEndpointAddress): ServerCoreEndpointClient =
            DefaultServerCoreEndpointClient(
              address = address,
              credentials = credentialsProvider.core,
              self = node,
              context = EndpointContext(rawConfig.getConfig("server.core.context")),
              maxChunkSize = rawConfig.getInt("server.core.max-chunk-size"),
              config = PoolClient.Config(
                minBackoff = rawConfig.getDuration("server.core.retry.min-backoff").toMillis.millis,
                maxBackoff = rawConfig.getDuration("server.core.retry.max-backoff").toMillis.millis,
                randomFactor = rawConfig.getDouble("server.core.retry.random-factor"),
                maxRetries = rawConfig.getInt("server.core.retry.max-retries"),
                requestBufferSize = rawConfig.getInt("server.core.request-buffer-size")
              )
            )

          def createServerApiEndpointClient(apiUrl: String, coreClient: ServerCoreEndpointClient): ServerApiEndpointClient = {
            val apiClient = DefaultServerApiEndpointClient(
              apiUrl = apiUrl,
              credentials = credentialsProvider.api,
              decryption = DefaultServerApiEndpointClient.DecryptionContext(
                core = coreClient,
                deviceSecret = deviceSecret,
                decoder = encryption
              ),
              self = deviceSecret.device,
              context = EndpointContext(rawConfig.getConfig("server.api.context")),
              config = PoolClient.Config(
                minBackoff = rawConfig.getDuration("server.api.retry.min-backoff").toMillis.millis,
                maxBackoff = rawConfig.getDuration("server.api.retry.max-backoff").toMillis.millis,
                randomFactor = rawConfig.getDouble("server.api.retry.random-factor"),
                maxRetries = rawConfig.getInt("server.api.retry.max-retries"),
                requestBufferSize = rawConfig.getInt("server.api.request-buffer-size")
              )
            )

            CachedServerApiEndpointClient(
              config = CachedServerApiEndpointClient.Config(
                initialCapacity = rawConfig.getInt("server.api.cache.initial-capacity"),
                maximumCapacity = rawConfig.getInt("server.api.cache.maximum-capacity"),
                timeToLive = rawConfig.getDuration("server.api.cache.time-to-live").toMillis.millis,
                timeToIdle = rawConfig.getDuration("server.api.cache.time-to-idle").toMillis.millis
              ),
              underlying = apiClient
            )
          }

          def createServiceDiscoveryClient(apiUrl: String): ServiceDiscoveryClient =
            HttpServiceDiscoveryClient(
              apiUrl = apiUrl,
              credentials = credentialsProvider.api,
              attributes = ClientDiscoveryAttributes(
                user = user,
                device = device,
                node = node
              ),
              context = EndpointContext(rawConfig.getConfig("server.api.context"))
            )

          val coreClient: ServerCoreEndpointClient = createServerCoreEndpointClient(
            address = HttpEndpointAddress(rawConfig.getString("server.core.address"))
          )

          val apiClient: ServerApiEndpointClient = createServerApiEndpointClient(
            apiUrl = rawConfig.getString("server.api.url"),
            coreClient = coreClient
          )

          val discoveryClient: ServiceDiscoveryClient = createServiceDiscoveryClient(
            apiUrl = rawConfig.getString("server.api.url")
          )

          val clientFactory = ServiceApiClientFactory(
            createServerCoreEndpointClient = createServerCoreEndpointClient,
            createServerApiEndpointClient = createServerApiEndpointClient,
            createServiceDiscoveryClient = createServiceDiscoveryClient
          )

          ServiceDiscoveryProvider(
            interval = rawConfig.getDuration("server.discovery.interval").toMillis.millis,
            initialClients = Seq(coreClient, apiClient, discoveryClient),
            clientFactory = clientFactory
          )
        }
      )
      .flatten
      .map { provider =>
        val discoveredClients = Clients(provider)

        telemetry.analytics.persistence.foreach { persistence =>
          persistence.withClientProvider(AnalyticsClient.Provider(() => discoveredClients.api))
        }

        new ApiClients {
          override val clients: Clients = discoveredClients
        }
      }
  }

  final case class ClientDiscoveryAttributes(
    user: User.Id,
    device: Device.Id,
    node: Node.Id
  ) extends ServiceDiscoveryClient.Attributes

  class ServiceApiClientFactory(
    createServerCoreEndpointClient: HttpEndpointAddress => ServerCoreEndpointClient,
    createServerApiEndpointClient: (String, ServerCoreEndpointClient) => ServerApiEndpointClient,
    createServiceDiscoveryClient: String => ServiceDiscoveryClient
  ) extends ServiceApiClient.Factory {
    @SuppressWarnings(Array("org.wartremover.warts.Throw"))
    override def create(endpoint: ServiceApiEndpoint.Api, coreClient: ServiceApiClient): ServiceApiClient =
      createServerApiEndpointClient(
        endpoint.uri,
        coreClient match {
          case client: ServerCoreEndpointClient =>
            client

          case other =>
            throw new IllegalArgumentException(
              s"Cannot create API endpoint client with core client of type [${other.getClass.getSimpleName}]"
            )
        }
      )

    @SuppressWarnings(Array("org.wartremover.warts.Throw"))
    override def create(endpoint: ServiceApiEndpoint.Core): ServiceApiClient =
      endpoint.address match {
        case address: HttpEndpointAddress =>
          createServerCoreEndpointClient(address)

        case address =>
          throw new IllegalArgumentException(
            s"Cannot create core endpoint client for address of type [${address.getClass.getSimpleName}]"
          )
      }

    override def create(endpoint: ServiceApiEndpoint.Discovery): ServiceApiClient =
      createServiceDiscoveryClient(endpoint.uri)
  }

  object ServiceApiClientFactory {
    def apply(
      createServerCoreEndpointClient: HttpEndpointAddress => ServerCoreEndpointClient,
      createServerApiEndpointClient: (String, ServerCoreEndpointClient) => ServerApiEndpointClient,
      createServiceDiscoveryClient: String => ServiceDiscoveryClient
    ): ServiceApiClientFactory = new ServiceApiClientFactory(
      createServerCoreEndpointClient = createServerCoreEndpointClient,
      createServerApiEndpointClient = createServerApiEndpointClient,
      createServiceDiscoveryClient = createServiceDiscoveryClient
    )
  }
}
