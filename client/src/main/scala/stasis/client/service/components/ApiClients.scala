package stasis.client.service.components

import java.util.UUID

import stasis.client.api.clients._
import stasis.core.networking.http.HttpEndpointAddress
import stasis.core.security.tls.EndpointContext

import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.Try

trait ApiClients {
  def clients: Clients
}

object ApiClients {
  def apply(base: Base, secrets: Secrets): Future[ApiClients] = {
    import base._
    import secrets._

    Future.fromTry(
      Try {
        val coreClient: ServerCoreEndpointClient = new DefaultServerCoreEndpointClient(
          address = HttpEndpointAddress(rawConfig.getString("server.core.address")),
          credentials = credentialsProvider.core,
          self = UUID.fromString(rawConfig.getString("server.authentication.client-id")),
          context = EndpointContext.fromConfig(rawConfig.getConfig("server.core.context")),
          requestBufferSize = rawConfig.getInt("server.core.request-buffer-size")
        )

        val apiClient: ServerApiEndpointClient = new DefaultServerApiEndpointClient(
          apiUrl = rawConfig.getString("server.api.url"),
          credentials = credentialsProvider.api,
          decryption = DefaultServerApiEndpointClient.DecryptionContext(
            core = coreClient,
            deviceSecret = deviceSecret,
            decoder = encryption
          ),
          self = deviceSecret.device,
          context = EndpointContext.fromConfig(rawConfig.getConfig("server.api.context")),
          requestBufferSize = rawConfig.getInt("server.api.request-buffer-size")
        )

        val cachedApiClient = new CachedServerApiEndpointClient(
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
