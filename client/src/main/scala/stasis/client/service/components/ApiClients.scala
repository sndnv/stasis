package stasis.client.service.components

import java.util.UUID

import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.Try

import stasis.client.api.clients._
import stasis.core.api.PoolClient
import stasis.core.networking.http.HttpEndpointAddress
import stasis.layers.security.tls.EndpointContext

trait ApiClients {
  def clients: Clients
}

object ApiClients {
  def apply(base: Base, secrets: Secrets): Future[ApiClients] = {
    import base._
    import secrets._

    Future.fromTry(
      Try {
        val coreClient: ServerCoreEndpointClient = DefaultServerCoreEndpointClient(
          address = HttpEndpointAddress(rawConfig.getString("server.core.address")),
          credentials = credentialsProvider.core,
          self = UUID.fromString(rawConfig.getString("server.core.node-id")),
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

        val apiClient: ServerApiEndpointClient = DefaultServerApiEndpointClient(
          apiUrl = rawConfig.getString("server.api.url"),
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

        val cachedApiClient = CachedServerApiEndpointClient(
          config = CachedServerApiEndpointClient.Config(
            initialCapacity = rawConfig.getInt("server.api.cache.initial-capacity"),
            maximumCapacity = rawConfig.getInt("server.api.cache.maximum-capacity"),
            timeToLive = rawConfig.getDuration("server.api.cache.time-to-live").toMillis.millis,
            timeToIdle = rawConfig.getDuration("server.api.cache.time-to-idle").toMillis.millis
          ),
          underlying = apiClient
        )

        new ApiClients {
          override val clients: Clients = Clients(
            api = cachedApiClient,
            core = coreClient
          )
        }
      }
    )
  }
}
